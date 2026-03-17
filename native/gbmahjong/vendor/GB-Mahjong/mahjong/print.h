#ifndef __MAHJONG_PRINT_H__
#define __MAHJONG_PRINT_H__

#include "pack.h"
#include "tile.h"
#include <string>

namespace mahjong {

inline std::string PackToEmojiString(const Pack &) {
    return {};
}

inline void StdPrintTile(const Tile &) {
}

} // namespace mahjong

#endif
