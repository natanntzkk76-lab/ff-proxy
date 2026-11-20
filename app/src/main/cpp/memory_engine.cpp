// memory_engine.cpp — Free Fire entity reader + W2S projection
// Runs INSIDE the game process (post-injection) so it reads its own memory.
// Language: C++17, Android NDK, arm64-v8a

#include <cstdint>
#include <cstring>
#include <cmath>
#include <string>
#include <vector>
#include "offsets.h"

struct Vec3 { float x, y, z; };
struct Mat4 { float m[16]; };

// Direct pointer dereference — valid because this code runs as the game UID.
static inline uintptr_t deref(uintptr_t addr) {
    return *reinterpret_cast<uintptr_t*>(addr);
}
static inline Vec3 readVec3(uintptr_t addr) {
    return *reinterpret_cast<Vec3*>(addr);
}
static inline float readF(uintptr_t addr) {
    return *reinterpret_cast<float*>(addr);
}
static inline int readI(uintptr_t addr) {
    return *reinterpret_cast<int*>(addr);
}
static inline bool readB(uintptr_t addr) {
    return *reinterpret_cast<bool*>(addr);
}

// Read a Unity ManagedString (System.String) into UTF-16 then ASCII.
static std::string readManagedString(uintptr_t strPtr) {
    if (!strPtr) return "";
    int len = readI(strPtr + FF::MANAGED_STRING_LENGTH);
    if (len <= 0 || len > 64) return "";
    const char16_t* data = reinterpret_cast<const char16_t*>(strPtr + FF::MANAGED_STRING_DATA);
    std::string out;
    for (int i = 0; i < len; i++) out += static_cast<char>(data[i]);
    return out;
}

// Read the 4x4 worldToCamera matrix from the main camera transform.
static Mat4 readViewMatrix(uintptr_t gameBase) {
    uintptr_t camXform = deref(gameBase + FF::MAGNET_MAIN_CAMERA_XFORM);
    uintptr_t matrixAddr = camXform + FF::VIEW_MATRIX_OFFSET;
    Mat4 mat;
    std::memcpy(mat.m, reinterpret_cast<void*>(matrixAddr), sizeof(Mat4));
    return mat;
}

// World-to-Screen projection. Returns false if behind camera.
static bool worldToScreen(const Vec3& world, const Mat4& view,
                          int screenW, int screenH, int& outX, int& outY) {
    // clip = view * (world, 1.0)  — column-major Unity layout
    float clipX = view.m[0]*world.x + view.m[4]*world.y + view.m[8]*world.z  + view.m[12];
    float clipY = view.m[1]*world.x + view.m[5]*world.y + view.m[9]*world.z  + view.m[13];
    float clipW = view.m[3]*world.x + view.m[7]*world.y + view.m[11]*world.z + view.m[15];

    if (clipW <= 0.001f) return false; // behind camera

    float ndcX = clipX / clipW;
    float ndcY = clipY / clipW;

    outX = static_cast<int>((ndcX * 0.5f + 0.5f) * screenW);
    outY = static_cast<int>((1.0f - (ndcY * 0.5f + 0.5f)) * screenH);
    return true;
}

struct Entity {
    uintptr_t ptr;
    Vec3 head;
    Vec3 hip;
    int screenHeadX, screenHeadY;
    int screenHipX, screenHipY;
    std::string name;
    int team;
    bool isFiring;
    bool isVisible;
    bool valid;
};

class EntityReader {
public:
    uintptr_t gameBase;
    int screenW, screenH;

    EntityReader(uintptr_t base, int w, int h) : gameBase(base), screenW(w), screenH(h) {}

    // Walk: base -> game -> match -> entities array
    bool getEntities(std::vector<Entity>& out) {
        out.clear();
        uintptr_t currentGame = deref(gameBase + FF::GAME_CURRENT_GAME);
        if (!currentGame) return false;
        uintptr_t match = deref(currentGame + FF::GAME_MATCH);
        if (!match) return false;
        uintptr_t entitiesList = deref(match + FF::MATCH_ENTITIES);
        if (!entitiesList) return false;

        // Unity List<T>: _items at 0x8 (RE-DERIVE if list layout differs), _size at 0x10
        uintptr_t itemsPtr = deref(entitiesList + 0x8);
        int count = readI(entitiesList + 0x10);
        if (!itemsPtr || count <= 0 || count > 200) return false;

        Mat4 view = readViewMatrix(gameBase);

        for (int i = 0; i < count; i++) {
            uintptr_t ent = deref(itemsPtr + (i * sizeof(uintptr_t)));
            if (!ent) continue;

            Entity e{};
            e.ptr = ent;
            e.valid = false;

            // Skip self
            uintptr_t local = deref(match + FF::MATCH_LOCAL_PLAYER);
            if (ent == local) continue;

            e.team = readI(ent + FF::PLAYER_TEAM);
            e.isFiring = readB(ent + FF::MAGNET_IS_FIRING);
            e.isVisible = readB(ent + FF::AVATAR_IS_VISIBLE);
            e.name = readManagedString(ent + FF::PLAYER_NICK_NAME);

            // Bones are Transform pointers; world pos is the transform's position field.
            uintptr_t headXform = deref(ent + FF::BONE_HEAD);
            uintptr_t hipXform  = deref(ent + FF::BONE_HIP);
            if (!headXform || !hipXform) continue;

            // Transform internal position: xform -> native transform -> pos (RE-DERIVE chain)
            uintptr_t headNode = deref(headXform + FF::TRANSFORM_NODE_TRANSFORM);
            uintptr_t hipNode  = deref(hipXform + FF::TRANSFORM_NODE_TRANSFORM);
            if (!headNode || !hipNode) continue;

            e.head = readVec3(headNode + 0x38); // position in TransformInternal
            e.hip  = readVec3(hipNode + 0x38);

            if (!worldToScreen(e.head, view, screenW, screenH, e.screenHeadX, e.screenHeadY)) continue;
            worldToScreen(e.hip, view, screenW, screenH, e.screenHipX, e.screenHipY);

            e.valid = true;
            out.push_back(std::move(e));
        }
        return true;
    }
};
