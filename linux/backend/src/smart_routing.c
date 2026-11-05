/* SPDX-License-Identifier: GPL-3.0-or-later
 * Packet layout derived from LibrePods AACPManager.kt, commit 89356c08.
 * See linux/UPSTREAM.md. No identity changes or pairing keys are requested.
 */
#include "smart_routing.h"
#include <bluetooth/bluetooth.h>
#include <string.h>
static void put(uint8_t *b, size_t *n, const void *s, size_t z)
{ memcpy(b + *n, s, z); *n += z; }
#define STR(s) put(b, &n, s, sizeof(s)-1)
#define BYTES(...) do { const uint8_t x[] = {__VA_ARGS__}; put(b, &n, x, sizeof x); } while (0)
bool smart_routing_build(const char *peer, const uint8_t local[6],
                         uint8_t media[144], uint8_t hijack[112])
{
    bdaddr_t remote, adapter;
    if (!peer || !local || strlen(peer) != 17 || str2ba(peer, &remote)) return false;
    for (size_t i = 0; i < 17; i++) {
        if (i % 3 == 2 ? peer[i] != ':' : !g_ascii_isxdigit(peer[i])) return false;
    }
    memcpy(&adapter, local, 6);
    char self[18]; ba2str(&adapter, self);
    uint8_t b[256] = {4,0,4,0,0x10,0}; size_t n = 6;
    put(b,&n,&remote,6); BYTES(0x82,0,1,0xe5,0x4a);
    STR("PlayingApp"); BYTES(0x56); STR("com.google.ios.youtube");
    BYTES(0x52); STR("HostStreamingState"); BYTES(0x42); STR("NO");
    BYTES(0x49); STR("btAddress"); BYTES(0x51); put(b,&n,self,17);
    STR("btName"); BYTES(0x47); STR("Android"); BYTES(0x58);
    STR("otherDevice"); STR("AudioCategory"); BYTES(0x31,0x2d,1);
    if (n > 144) return false;
    memcpy(media,b,144);
    memset(b,0,sizeof b); b[0]=4; b[2]=4; b[4]=0x10; n=6;
    put(b,&n,&remote,6); BYTES(0x62,0,1,0xe5,0x4a);
    STR("localscore"); BYTES(0x30,0x64,0x46); STR("reason");
    BYTES(0x48); STR("Hijackv2"); BYTES(0x51); STR("audioRoutingScore");
    BYTES(0x31,0x2d,1,0x5f); STR("audioRoutingSetOwnershipToFalse");
    BYTES(1,0x4b); STR("remotescore"); BYTES(0xa5);
    if (n != 112) return false;
    memcpy(hijack,b,112);
    return true;
}
bool smart_routing_send(BluetoothConnection *connection, const char *peer,
                        const uint8_t local[6])
{
    uint8_t media[144], hijack[112];
    if (!smart_routing_build(peer,local,media,hijack)) return false;
    return bt_connection_send(connection,media,sizeof media) == sizeof media &&
           bt_connection_send(connection,hijack,sizeof hijack) == sizeof hijack;
}
