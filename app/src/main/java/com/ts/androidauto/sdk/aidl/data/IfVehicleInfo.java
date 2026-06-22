package com.ts.androidauto.sdk.aidl.data;

import android.os.Parcel;
import android.os.Parcelable;
import com.ts.androidauto.sdk.common.VehicleConst;

public final class IfVehicleInfo implements Parcelable {
    public int mAapTouchPointOffsetX;
    public int mAapTouchPointOffsetY;
    public AapVideoConfig mAapVideoConfig1080p = new AapVideoConfig();
    public AapVideoConfig mAapVideoConfig480p = new AapVideoConfig();
    public AapVideoConfig mAapVideoConfig720p = new AapVideoConfig();
    public String mAoaSerialNumber;
    public String mDisplayName;
    public String mHeadUnitMaker;
    public String mHeadUnitModel;
    public String mHeadUnitSwBuild;
    public String mHeadUnitSwVer;
    public boolean mIsNeedShowProjectionOnPhoneCall;
    public boolean mIsSupportDisplayNaviDataUpdate;
    public boolean mIsSupportWireless;
    public boolean mIsTruck;
    public VehicleConst.AapHardkeyEvent[] mSupportHardKeyList;
    public int mTouchScreenHeight;
    public int mTouchScreenWidth;
    public VehicleConst.AapVehicleDrivePosition mVehicleDriverPosition;
    public VehicleConst.AapEvConnectorType[] mVehicleEvConnectorTypeList;
    public VehicleConst.AapVehicleFuelType[] mVehicleFuelTypeList;
    public String mVehicleId;
    public String mVehicleMaker;
    public String mVehicleModel;
    public String mVehicleYear;
    public int mViewingDistance;

    public static class AapVideoConfig implements Parcelable {
        public int mAapDisplayAreaHeight;
        public int mAapDisplayAreaMarginLeft;
        public int mAapDisplayAreaMarginTop;
        public int mAapDisplayAreaWidth;
        public int mAapResolutionHeight;
        public float mAapResolutionPixelAspect;
        public int mAapResolutionRealDpi;
        public int mAapResolutionReportDpi;
        public int mAapResolutionWidth;
        public int mAapSurfaceViewHeight;
        public int mAapSurfaceViewScrollX;
        public int mAapSurfaceViewScrollY;
        public int mAapSurfaceViewWidth;

        public AapVideoConfig() {}

        protected AapVideoConfig(Parcel in) {
            mAapDisplayAreaWidth = in.readInt();
            mAapDisplayAreaHeight = in.readInt();
            mAapDisplayAreaMarginLeft = in.readInt();
            mAapDisplayAreaMarginTop = in.readInt();
            mAapResolutionWidth = in.readInt();
            mAapResolutionHeight = in.readInt();
            mAapResolutionReportDpi = in.readInt();
            mAapResolutionRealDpi = in.readInt();
            mAapResolutionPixelAspect = in.readFloat();
            mAapSurfaceViewWidth = in.readInt();
            mAapSurfaceViewHeight = in.readInt();
            mAapSurfaceViewScrollX = in.readInt();
            mAapSurfaceViewScrollY = in.readInt();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mAapDisplayAreaWidth);
            dest.writeInt(mAapDisplayAreaHeight);
            dest.writeInt(mAapDisplayAreaMarginLeft);
            dest.writeInt(mAapDisplayAreaMarginTop);
            dest.writeInt(mAapResolutionWidth);
            dest.writeInt(mAapResolutionHeight);
            dest.writeInt(mAapResolutionReportDpi);
            dest.writeInt(mAapResolutionRealDpi);
            dest.writeFloat(mAapResolutionPixelAspect);
            dest.writeInt(mAapSurfaceViewWidth);
            dest.writeInt(mAapSurfaceViewHeight);
            dest.writeInt(mAapSurfaceViewScrollX);
            dest.writeInt(mAapSurfaceViewScrollY);
        }

        public static final Creator<AapVideoConfig> CREATOR =
                new Creator<AapVideoConfig>() {
                    @Override
                    public AapVideoConfig createFromParcel(Parcel in) {
                        return new AapVideoConfig(in);
                    }

                    @Override
                    public AapVideoConfig[] newArray(int size) {
                        return new AapVideoConfig[size];
                    }
                };
    }

    public IfVehicleInfo() {}

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mTouchScreenWidth);
        dest.writeInt(mTouchScreenHeight);
        dest.writeInt(mAapTouchPointOffsetX);
        dest.writeInt(mAapTouchPointOffsetY);
        dest.writeParcelable(mAapVideoConfig480p, flags);
        dest.writeParcelable(mAapVideoConfig720p, flags);
        dest.writeParcelable(mAapVideoConfig1080p, flags);
        dest.writeString(mVehicleMaker);
        dest.writeString(mVehicleModel);
        dest.writeString(mVehicleYear);
        dest.writeString(mVehicleId);
        if (mVehicleDriverPosition == null) {
            mVehicleDriverPosition = VehicleConst.AapVehicleDrivePosition.AAP_DRIVE_UNKNOWN;
        }
        dest.writeInt(mVehicleDriverPosition.ordinal());
        dest.writeString(mHeadUnitMaker);
        dest.writeString(mHeadUnitModel);
        dest.writeString(mHeadUnitSwBuild);
        dest.writeString(mHeadUnitSwVer);
        dest.writeString(mDisplayName);
        dest.writeInt(mViewingDistance);
        dest.writeString(mAoaSerialNumber);
        if (mVehicleFuelTypeList == null) {
            mVehicleFuelTypeList = new VehicleConst.AapVehicleFuelType[0];
        }
        dest.writeInt(mVehicleFuelTypeList.length);
        for (VehicleConst.AapVehicleFuelType fuelType : mVehicleFuelTypeList) {
            dest.writeInt(
                    (fuelType != null ? fuelType : VehicleConst.AapVehicleFuelType.AAP_FUEL_UNKNOWN)
                            .ordinal());
        }
        if (mVehicleEvConnectorTypeList == null) {
            mVehicleEvConnectorTypeList = new VehicleConst.AapEvConnectorType[0];
        }
        dest.writeInt(mVehicleEvConnectorTypeList.length);
        for (VehicleConst.AapEvConnectorType connectorType : mVehicleEvConnectorTypeList) {
            dest.writeInt(
                    (connectorType != null
                                    ? connectorType
                                    : VehicleConst.AapEvConnectorType.AAP_EV_CONNECTOR_UNKNOWN)
                            .ordinal());
        }
        if (mSupportHardKeyList == null) {
            mSupportHardKeyList = new VehicleConst.AapHardkeyEvent[0];
        }
        dest.writeInt(mSupportHardKeyList.length);
        for (VehicleConst.AapHardkeyEvent hardKey : mSupportHardKeyList) {
            dest.writeInt(
                    (hardKey != null ? hardKey : VehicleConst.AapHardkeyEvent.AAP_KEYCODE_SEARCH)
                            .ordinal());
        }
        dest.writeInt(mIsTruck ? 1 : 0);
        dest.writeInt(mIsSupportWireless ? 1 : 0);
        dest.writeInt(mIsSupportDisplayNaviDataUpdate ? 1 : 0);
        dest.writeInt(mIsNeedShowProjectionOnPhoneCall ? 1 : 0);
    }

    public static final Creator<IfVehicleInfo> CREATOR =
            new Creator<IfVehicleInfo>() {
                @Override
                public IfVehicleInfo createFromParcel(Parcel in) {
                    throw new UnsupportedOperationException("IfVehicleInfo is write-only in this app");
                }

                @Override
                public IfVehicleInfo[] newArray(int size) {
                    return new IfVehicleInfo[size];
                }
            };
}
