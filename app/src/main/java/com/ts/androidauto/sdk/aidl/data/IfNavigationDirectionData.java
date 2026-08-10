package com.ts.androidauto.sdk.aidl.data;

import android.os.Parcel;
import android.os.Parcelable;

/* JADX INFO: loaded from: classes.dex */
public final class IfNavigationDirectionData implements Parcelable {
    public static final Parcelable.Creator<IfNavigationDirectionData> CREATOR = new Parcelable.Creator<IfNavigationDirectionData>() { // from class: com.ts.androidauto.sdk.aidl.data.IfNavigationDirectionData.1
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public IfNavigationDirectionData createFromParcel(Parcel in) {
            return new IfNavigationDirectionData(in);
        }

        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public IfNavigationDirectionData[] newArray(int size) {
            return new IfNavigationDirectionData[size];
        }
    };
    private int mIsHighlighted;
    private int mShape;

    public IfNavigationDirectionData() {
    }

    public IfNavigationDirectionData(Parcel in) {
        this.mShape = in.readInt();
        this.mIsHighlighted = in.readInt();
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(this.mShape);
        parcel.writeInt(this.mIsHighlighted);
    }

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    public int getShape() {
        return this.mShape;
    }

    public void setShape(int shape) {
        this.mShape = shape;
    }

    public int getHighlighted() {
        return this.mIsHighlighted;
    }

    public void setHighlighted(int highlighted) {
        this.mIsHighlighted = highlighted;
    }

    public String toString() {
        return "IfNavigationDirectionData{mShape=" + this.mShape + ", mIsHighlighted=" + this.mIsHighlighted + '}';
    }
}
