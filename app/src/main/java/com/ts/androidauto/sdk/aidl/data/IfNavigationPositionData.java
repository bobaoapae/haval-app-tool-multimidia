package com.ts.androidauto.sdk.aidl.data;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public final class IfNavigationPositionData implements Parcelable {
    public static final Parcelable.Creator<IfNavigationPositionData> CREATOR = new Parcelable.Creator<IfNavigationPositionData>() { // from class: com.ts.androidauto.sdk.aidl.data.IfNavigationPositionData.1
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public IfNavigationPositionData createFromParcel(Parcel in) {
            return new IfNavigationPositionData(in);
        }

        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public IfNavigationPositionData[] newArray(int size) {
            return new IfNavigationPositionData[size];
        }
    };
    private String mCurrentRoad;
    private int mDisplayUnits;
    private String mDisplayValue;
    private List<IfNavigationDestDistanceData> mIfNavigationDestDistanceData;
    private int mMeters;
    private long mTimeSeconds;

    public String getDisplayValue() {
        return this.mDisplayValue;
    }

    public void setDisplayValue(String displayValue) {
        this.mDisplayValue = displayValue;
    }

    public int getMeters() {
        return this.mMeters;
    }

    public void setMeters(int meters) {
        this.mMeters = meters;
    }

    public int getDisplayUnits() {
        return this.mDisplayUnits;
    }

    public void setDisplayUnits(int displayUnits) {
        this.mDisplayUnits = displayUnits;
    }

    public long getTimeSeconds() {
        return this.mTimeSeconds;
    }

    public void setTimeSeconds(long timeSeconds) {
        this.mTimeSeconds = timeSeconds;
    }

    public String getCurrentRoad() {
        return this.mCurrentRoad;
    }

    public void setCurrentRoad(String currentRoad) {
        this.mCurrentRoad = currentRoad;
    }

    public List<IfNavigationDestDistanceData> getIfNavigationDestDistanceData() {
        return this.mIfNavigationDestDistanceData;
    }

    public void setIfNavigationDestDistanceData(List<IfNavigationDestDistanceData> navigationDestDistanceData) {
        this.mIfNavigationDestDistanceData = navigationDestDistanceData;
    }

    public IfNavigationPositionData() {
    }

    public IfNavigationPositionData(Parcel in) {
        this.mDisplayValue = in.readString();
        this.mMeters = in.readInt();
        this.mDisplayUnits = in.readInt();
        this.mTimeSeconds = in.readLong();
        this.mCurrentRoad = in.readString();
        int sizeDestDistance = in.readInt();
        if (sizeDestDistance <= 0) {
            this.mIfNavigationDestDistanceData = null;
        } else {
            this.mIfNavigationDestDistanceData = in.createTypedArrayList(IfNavigationDestDistanceData.CREATOR);
        }
    }

    public String toString() {
        return "IfNavigationPositionData{mDisplayValue='" + this.mDisplayValue + "', mMeters=" + this.mMeters + ", mDisplayUnits=" + this.mDisplayUnits + ", mTimeSeconds=" + this.mTimeSeconds + ", mCurrentRoad=" + this.mCurrentRoad + ", mIfNavigationDestDistanceData=" + this.mIfNavigationDestDistanceData + '}';
    }

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(this.mDisplayValue);
        parcel.writeInt(this.mMeters);
        parcel.writeInt(this.mDisplayUnits);
        parcel.writeLong(this.mTimeSeconds);
        parcel.writeString(this.mCurrentRoad);
        if (this.mIfNavigationDestDistanceData != null && this.mIfNavigationDestDistanceData.size() > 0) {
            parcel.writeInt(this.mIfNavigationDestDistanceData.size());
            parcel.writeTypedList(this.mIfNavigationDestDistanceData);
        } else {
            parcel.writeInt(0);
        }
    }
}
