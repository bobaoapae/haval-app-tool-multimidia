package com.ts.androidauto.sdk.aidl.data;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public final class IfNavigationLaneData implements Parcelable {
    public static final Parcelable.Creator<IfNavigationLaneData> CREATOR = new Parcelable.Creator<IfNavigationLaneData>() { // from class: com.ts.androidauto.sdk.aidl.data.IfNavigationLaneData.1
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public IfNavigationLaneData createFromParcel(Parcel in) {
            return new IfNavigationLaneData(in);
        }

        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public IfNavigationLaneData[] newArray(int size) {
            return new IfNavigationLaneData[size];
        }
    };
    private List<IfNavigationDirectionData> mIfNavigationDirectionData;

    public IfNavigationLaneData() {
    }

    public IfNavigationLaneData(Parcel in) {
        int sizeDirection = in.readInt();
        if (sizeDirection <= 0) {
            this.mIfNavigationDirectionData = null;
        } else {
            this.mIfNavigationDirectionData = in.createTypedArrayList(IfNavigationDirectionData.CREATOR);
        }
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel parcel, int i) {
        if (this.mIfNavigationDirectionData != null && this.mIfNavigationDirectionData.size() > 0) {
            parcel.writeInt(this.mIfNavigationDirectionData.size());
            parcel.writeTypedList(this.mIfNavigationDirectionData);
        } else {
            parcel.writeInt(0);
        }
    }

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    public List<IfNavigationDirectionData> getIfNavigationDirectionData() {
        return this.mIfNavigationDirectionData;
    }

    public void setIfNavigationDirectionData(List<IfNavigationDirectionData> ifNavigationDirectionData) {
        this.mIfNavigationDirectionData = ifNavigationDirectionData;
    }

    public String toString() {
        return "IfNavigationLaneData{mIfNavigationDirectionData=" + this.mIfNavigationDirectionData + '}';
    }
}
