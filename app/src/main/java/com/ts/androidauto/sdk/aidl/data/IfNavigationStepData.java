package com.ts.androidauto.sdk.aidl.data;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public final class IfNavigationStepData implements Parcelable {
    public static final Parcelable.Creator<IfNavigationStepData> CREATOR = new Parcelable.Creator<IfNavigationStepData>() { // from class: com.ts.androidauto.sdk.aidl.data.IfNavigationStepData.1
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public IfNavigationStepData createFromParcel(Parcel in) {
            return new IfNavigationStepData(in);
        }

        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public IfNavigationStepData[] newArray(int size) {
            return new IfNavigationStepData[size];
        }
    };
    private List<String> mCueData;
    private int mEvent;
    private List<IfNavigationLaneData> mLaneData;
    private String mRoad;
    private int mTurnAngle;
    private int mTurnNumber;

    public IfNavigationStepData() {
    }

    public List<IfNavigationLaneData> getLaneData() {
        return this.mLaneData;
    }

    public void setLaneData(List<IfNavigationLaneData> laneData) {
        this.mLaneData = laneData;
    }

    public List<String> getNavigationCue() {
        return this.mCueData;
    }

    public void setNavigationCue(List<String> navigationCue) {
        this.mCueData = navigationCue;
    }

    public int getEvent() {
        return this.mEvent;
    }

    public int getTurnAngle() {
        return this.mTurnAngle;
    }

    public int getTurnNumber() {
        return this.mTurnNumber;
    }

    public String getRoad() {
        return this.mRoad;
    }

    public void setEvent(int event) {
        this.mEvent = event;
    }

    public void setRoad(String road) {
        this.mRoad = road;
    }

    public void setTurnAngle(int turnAngle) {
        this.mTurnAngle = turnAngle;
    }

    public void setTurnNumber(int turnNumber) {
        this.mTurnNumber = turnNumber;
    }

    public IfNavigationStepData(Parcel in) {
        this.mRoad = in.readString();
        this.mEvent = in.readInt();
        this.mTurnAngle = in.readInt();
        this.mTurnNumber = in.readInt();
        int sizeCue = in.readInt();
        if (sizeCue <= 0) {
            this.mCueData = null;
        } else {
            this.mCueData = in.createStringArrayList();
        }
        int sizeLane = in.readInt();
        if (sizeLane <= 0) {
            this.mLaneData = null;
        } else {
            this.mLaneData = in.createTypedArrayList(IfNavigationLaneData.CREATOR);
        }
    }

    public String toString() {
        return "IfNavigationStepData{mRoad='" + this.mRoad + "', mEvent=" + this.mEvent + ", mTurnAngle=" + this.mTurnAngle + ", mTurnNumber=" + this.mTurnNumber + ", mLaneData=" + this.mLaneData + ", mCueData=" + this.mCueData + '}';
    }

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(this.mRoad);
        parcel.writeInt(this.mEvent);
        parcel.writeInt(this.mTurnAngle);
        parcel.writeInt(this.mTurnNumber);
        if (this.mCueData != null && this.mCueData.size() > 0) {
            parcel.writeInt(this.mCueData.size());
            parcel.writeStringList(this.mCueData);
        } else {
            parcel.writeInt(0);
        }
        if (this.mLaneData != null && this.mLaneData.size() > 0) {
            parcel.writeInt(this.mLaneData.size());
            parcel.writeTypedList(this.mLaneData);
        } else {
            parcel.writeInt(0);
        }
    }
}
