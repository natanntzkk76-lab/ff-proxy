// offsets.h — Free Fire IL2CPP pointer chain (from c!addos dump)
// Re-derive per build. These shift on every Garena patch.
#ifndef OFFSETS_H
#define OFFSETS_H

namespace FF {
    // Root chain
    constexpr uintptr_t GAME_CURRENT_GAME        = 0x8;
    constexpr uintptr_t GAME_MATCH               = 0x90;
    constexpr uintptr_t MATCH_ENTITIES           = 0x128;
    constexpr uintptr_t MATCH_LOCAL_PLAYER       = 0xD8;

    // Player structure
    constexpr uintptr_t PLAYER_CACHED_TRANSFORM  = 0x58;
    constexpr uintptr_t PLAYER_HEAD              = 0x6A0;
    constexpr uintptr_t PLAYER_NICK_NAME         = 0x490;
    constexpr uintptr_t PLAYER_TEAM              = 0x430;
    constexpr uintptr_t PLAYER_FOLLOW_CAMERA     = 0x690;
    constexpr uintptr_t PLAYER_DATA_POOL         = 0x70;

    // Bones (ESP box + aimbot targets)
    constexpr uintptr_t BONE_HEAD                = 0x6A0;
    constexpr uintptr_t BONE_NECK                = 0x6B0;
    constexpr uintptr_t BONE_HIP                 = 0x6A8;
    constexpr uintptr_t BONE_ROOT                = 0x6C8;
    constexpr uintptr_t BONE_LEFT_SHOULDER       = 0x708;
    constexpr uintptr_t BONE_RIGHT_SHOULDER      = 0x710;
    constexpr uintptr_t BONE_LEFT_FOOT           = 0x6E8;
    constexpr uintptr_t BONE_RIGHT_FOOT          = 0x6F0;

    // State
    constexpr uintptr_t MAGNET_IS_FIRING         = 0x848;
    constexpr uintptr_t MAGNET_IS_FEMALE         = 0xC14;
    constexpr uintptr_t MAGNET_MAIN_CAMERA_XFORM = 0x3E8;
    constexpr uintptr_t AVATAR_IS_VISIBLE        = 0x101;

    // Managed string (nickname)
    constexpr uintptr_t MANAGED_STRING_DATA      = 0x14;
    constexpr uintptr_t MANAGED_STRING_LENGTH    = 0x10;

    // Transform node
    constexpr uintptr_t TRANSFORM_NODE_TRANSFORM = 0x10;
    constexpr uintptr_t CAMERA_MANAGED_CAMERA    = 0x30;

    // RE-DERIVE: worldToCameraMatrix offset from camera transform.
    // Unity default is 0x10 for the Matrix4x4 in the native camera object.
    constexpr uintptr_t VIEW_MATRIX_OFFSET       = 0x10;
}
#endif
