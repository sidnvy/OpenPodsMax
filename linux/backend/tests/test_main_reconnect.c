/* SPDX-License-Identifier: GPL-3.0-or-later
 * Exercise the real reconnect entry point; no Bluetooth sockets are opened.
 */
#define main openpodsmax_daemon_main
#include "../src/main.c"
#undef main
static const char *old_address;
bool __wrap_bt_connection_connect(BluetoothConnection *conn, const char *address)
{
    g_assert_nonnull(conn);
    /* The caller may receive its own cached address. It must pass the fresh
     * owned copy onward, not the just-freed input buffer. */
    g_assert_true(address != old_address);
    g_assert_true(address == app.pending_address);
    g_assert_cmpstr(address, ==, "00:11:22:33:44:55");
    return true;
}
static void reconnect_uses_live_address(void)
{
    g_setenv("OPENPODS_HANDOFF_HEADSET_MAC", "00:11:22:33:44:55", TRUE);
    app.pending_address = g_strdup("00:11:22:33:44:55");
    app.pending_name = g_strdup("Test headphones");
    old_address = app.pending_address;
    connect_to_airpods(app.pending_address, app.pending_name);
    g_assert_cmpstr(app.pending_name, ==, "Test headphones");
    bt_connection_free(app.bt_conn); app.bt_conn = NULL;
    g_clear_pointer(&app.pending_address, g_free);
    g_clear_pointer(&app.pending_name, g_free);
}
int main(int argc, char **argv)
{
    g_test_init(&argc, &argv, NULL);
    g_test_add_func("/main/reconnect/owned-address", reconnect_uses_live_address);
    return g_test_run();
}
