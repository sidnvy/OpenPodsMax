package android.bluetooth; public final class BluetoothDevice {
 public static final int BOND_BONDED=12;
 public static final String ACTION_ACL_CONNECTED="android.bluetooth.device.action.ACL_CONNECTED", EXTRA_DEVICE="android.bluetooth.device.extra.DEVICE";
 private final String address;
 public BluetoothDevice(){this(null);} public BluetoothDevice(String address){this.address=address;}
 public String getAddress(){return address;}
 public int getBondState(){return BOND_BONDED;}
 public BluetoothSocket createInsecureL2capSocket(int psm){return BluetoothAdapter.nextSocket;}
}
