/* SPDX-License-Identifier: GPL-3.0-or-later */
#pragma once
#include "bluetooth.h"
bool smart_routing_build(const char *peer, const uint8_t local[6],
                         uint8_t media[144], uint8_t hijack[112]);
bool smart_routing_send(BluetoothConnection *connection, const char *peer,
                        const uint8_t local[6]);
