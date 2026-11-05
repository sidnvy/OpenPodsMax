/* SPDX-License-Identifier: GPL-3.0-or-later */
#include <glib.h>
#include <string.h>
#include "smart_routing.h"
static unsigned sends;
static bool fail_media;
ssize_t bt_connection_send(BluetoothConnection *conn, const uint8_t *bytes, size_t size)
{
    (void)conn;
    g_assert_cmpuint(bytes[4], ==, 0x10);
    g_assert_cmpuint(size, ==, sends == 0 ? 144 : 112);
    sends++;
    return fail_media ? -1 : (ssize_t)size;
}
static void bounded_packets(void)
{
    const uint8_t local[] = {6,5,4,3,2,1};
    struct { uint8_t before; uint8_t bytes[144]; uint8_t after; } media = {.before=0xa5,.after=0x5a};
    struct { uint8_t before; uint8_t bytes[112]; uint8_t after; } hijack = {.before=0xa5,.after=0x5a};
    g_assert_true(smart_routing_build("00:11:22:33:44:55",local,media.bytes,hijack.bytes));
    g_assert_cmpuint(media.before, ==, 0xa5); g_assert_cmpuint(media.after, ==, 0x5a);
    g_assert_cmpuint(hijack.before, ==, 0xa5); g_assert_cmpuint(hijack.after, ==, 0x5a);
    const uint8_t peer_wire[] = {0x55,0x44,0x33,0x22,0x11,0};
    g_assert_cmpmem(media.bytes+6,6,peer_wire,6);
    g_assert_cmpmem(hijack.bytes+6,6,peer_wire,6);
    /* The body lengths advertised to the remote host must match the frames. */
    g_assert_cmpuint(media.bytes[12]+14, ==, sizeof media.bytes);
    g_assert_cmpuint(hijack.bytes[12]+14, ==, sizeof hijack.bytes);
    /* Invalid private configuration must never produce outbound traffic. */
    sends=0;fail_media=false;
    g_assert_false(smart_routing_send(NULL,"not-an-address",local));
    g_assert_cmpuint(sends,==,0);
    g_assert_false(smart_routing_send(NULL,"GG:11:22:33:44:55",local));
    g_assert_cmpuint(sends,==,0);
    g_assert_true(smart_routing_send(NULL,"00:11:22:33:44:55",local));
    g_assert_cmpuint(sends,==,2);
    sends=0;fail_media=true;
    g_assert_false(smart_routing_send(NULL,"00:11:22:33:44:55",local));
    g_assert_cmpuint(sends,==,1); /* no Hijack after a failed information write */
}
int main(int argc,char **argv)
{
    g_test_init(&argc,&argv,NULL);
    g_test_add_func("/smart-routing/bounds-and-write-order",bounded_packets);
    return g_test_run();
}
