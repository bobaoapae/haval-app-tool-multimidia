package ts.car.dcm.common.data;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.HashMap;

public final class ConnDevice implements Parcelable {
    public static final Parcelable.Creator<ConnDevice> CREATOR =
            new Parcelable.Creator<ConnDevice>() {
                @Override
                public ConnDevice createFromParcel(Parcel source) {
                    return new ConnDevice(source);
                }

                @Override
                public ConnDevice[] newArray(int size) {
                    return new ConnDevice[size];
                }
            };

    private final HashMap<Integer, Integer> mConnectedTypeState = new HashMap<>();
    private final HashMap<Integer, Integer> mActiveCapabilityState = new HashMap<>();
    private final HashMap<Integer, Integer> mBtProfileState = new HashMap<>();
    private String mDeviceUuid;
    private String mFriendlyName;
    private int mDeviceType;
    private int mAccepted;
    private int mDeviceConnectedState;
    private String mUsbSerialNumber;
    private String mBtAddr;
    private String mWifiAddr;
    private String mWifiIpAddress;
    private String mLastUptime;
    private int mLastService;
    private boolean mLast;
    private boolean mFavorite;
    private int mCapability;
    private int mAvailableCapability;
    private int mActiveCapability;
    private int mAlwaysCapability;
    private boolean mInUsbDeviceMode;
    private String mUsbHidNode;
    private String mIapNode;
    private String mUsbNcmNode;
    private boolean mIsPaired;
    private long mBtDevicePairedTime;
    private long mWirelessCarplayConnectedTime;
    private long mUsbCarplayConnectedTime;
    private boolean mIsUsbOtgPort;
    private long mChangedMask;
    private boolean mIsSupportCpByUuid;

    private ConnDevice(Parcel source) {
        mDeviceUuid = source.readString();
        mFriendlyName = source.readString();
        mDeviceType = source.readInt();
        mAccepted = source.readInt();
        mDeviceConnectedState = source.readInt();
        source.readMap(mConnectedTypeState, Integer.class.getClassLoader());
        mUsbSerialNumber = source.readString();
        mBtAddr = source.readString();
        mWifiAddr = source.readString();
        mWifiIpAddress = source.readString();
        mLastUptime = source.readString();
        mLastService = source.readInt();
        mLast = source.readByte() != 0;
        mFavorite = source.readByte() != 0;
        mCapability = source.readInt();
        mAvailableCapability = source.readInt();
        mActiveCapability = source.readInt();
        source.readMap(mActiveCapabilityState, Integer.class.getClassLoader());
        mAlwaysCapability = source.readInt();
        source.readMap(mBtProfileState, Integer.class.getClassLoader());
        mInUsbDeviceMode = source.readByte() != 0;
        mUsbHidNode = source.readString();
        mIapNode = source.readString();
        mUsbNcmNode = source.readString();
        mIsPaired = source.readByte() != 0;
        mBtDevicePairedTime = source.readLong();
        mWirelessCarplayConnectedTime = source.readLong();
        mUsbCarplayConnectedTime = source.readLong();
        mIsUsbOtgPort = source.readByte() != 0;
        if (source.dataAvail() >= Long.BYTES) {
            mChangedMask = source.readLong();
        }
        if (source.dataAvail() > 0) {
            mIsSupportCpByUuid = source.readByte() != 0;
        }
    }

    public String getDeviceUuid() {
        return mDeviceUuid;
    }

    public String getFriendlyName() {
        return mFriendlyName;
    }

    public String getUsbSerialNumber() {
        return mUsbSerialNumber;
    }

    public String getBtAddr() {
        return mBtAddr;
    }

    public int getAvailableCapabilitys() {
        return mAvailableCapability;
    }

    public int getActiveCapability() {
        return mActiveCapability;
    }

    public int getDeviceConnectedState() {
        return mDeviceConnectedState;
    }

    public HashMap<Integer, Integer> getConnectedTypeState() {
        return mConnectedTypeState;
    }

    public int getActiveCapabilityState(int capability) {
        Integer state = mActiveCapabilityState.get(capability);
        return state != null ? state : 0;
    }

    public HashMap<Integer, Integer> getActiveCapabilityState() {
        return mActiveCapabilityState;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mDeviceUuid);
        dest.writeString(mFriendlyName);
        dest.writeInt(mDeviceType);
        dest.writeInt(mAccepted);
        dest.writeInt(mDeviceConnectedState);
        dest.writeMap(mConnectedTypeState);
        dest.writeString(mUsbSerialNumber);
        dest.writeString(mBtAddr);
        dest.writeString(mWifiAddr);
        dest.writeString(mWifiIpAddress);
        dest.writeString(mLastUptime);
        dest.writeInt(mLastService);
        dest.writeByte((byte) (mLast ? 1 : 0));
        dest.writeByte((byte) (mFavorite ? 1 : 0));
        dest.writeInt(mCapability);
        dest.writeInt(mAvailableCapability);
        dest.writeInt(mActiveCapability);
        dest.writeMap(mActiveCapabilityState);
        dest.writeInt(mAlwaysCapability);
        dest.writeMap(mBtProfileState);
        dest.writeByte((byte) (mInUsbDeviceMode ? 1 : 0));
        dest.writeString(mUsbHidNode);
        dest.writeString(mIapNode);
        dest.writeString(mUsbNcmNode);
        dest.writeByte((byte) (mIsPaired ? 1 : 0));
        dest.writeLong(mBtDevicePairedTime);
        dest.writeLong(mWirelessCarplayConnectedTime);
        dest.writeLong(mUsbCarplayConnectedTime);
        dest.writeByte((byte) (mIsUsbOtgPort ? 1 : 0));
        dest.writeLong(mChangedMask);
        dest.writeByte((byte) (mIsSupportCpByUuid ? 1 : 0));
    }
}
