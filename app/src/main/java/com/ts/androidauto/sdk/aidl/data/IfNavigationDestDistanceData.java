package com.ts.androidauto.sdk.aidl.data;

import android.os.Parcel;
import android.os.Parcelable;

/* JADX INFO: loaded from: classes.dex */
public final class IfNavigationDestDistanceData implements Parcelable {
    public static final Parcelable.Creator<IfNavigationDestDistanceData> CREATOR = new Parcelable.Creator<IfNavigationDestDistanceData>() { // from class: com.ts.androidauto.sdk.aidl.data.IfNavigationDestDistanceData.1
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public IfNavigationDestDistanceData createFromParcel(Parcel in) {
            return new IfNavigationDestDistanceData(in);
        }

        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public IfNavigationDestDistanceData[] newArray(int size) {
            return new IfNavigationDestDistanceData[size];
        }
    };
    private int mDisplayUnits;
    private String mDisplayValue;
    private String mEstimatedTime;
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

    public String getEstimatedTime() {
        return this.mEstimatedTime;
    }

    public void setEstimatedTime(String estimatedTime) {
        this.mEstimatedTime = estimatedTime;
    }

    public long getTimeSeconds() {
        return this.mTimeSeconds;
    }

    public void setTimeSeconds(long timeSeconds) {
        this.mTimeSeconds = timeSeconds;
    }

    public IfNavigationDestDistanceData() {
    }

    public IfNavigationDestDistanceData(Parcel in) {
        this.mDisplayValue = in.readString();
        this.mMeters = in.readInt();
        this.mDisplayUnits = in.readInt();
        this.mEstimatedTime = in.readString();
        this.mTimeSeconds = in.readLong();
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(this.mDisplayValue);
        parcel.writeInt(this.mMeters);
        parcel.writeInt(this.mDisplayUnits);
        parcel.writeString(this.mEstimatedTime);
        parcel.writeLong(this.mTimeSeconds);
    }

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    public String toString() {
        return "IfNavigationDestDistanceData{mDisplayValue='" + this.mDisplayValue + "', mMeters=" + this.mMeters + ", mDisplayUnits=" + this.mDisplayUnits + ", mEstimatedTime='" + this.mEstimatedTime + "', mTimeSeconds=" + this.mTimeSeconds + '}';
    }
}
