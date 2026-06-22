package com.beantechs.mediacenter.core_common.data

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable

class MediaInfo() : Parcelable {
        var mediaId: String? = null
        var channelId: String? = null
        var path: String? = null
        var cpId: String? = null
        var categoryId: String? = null
        var contentId: String? = null
        var albumId: String? = null
        var trackId: String? = null
        var title: String? = null
        var subTitle: String? = null
        var author: String? = null
        var imageUrl: String? = null
        var description: String? = null
        var intro: String? = null
        var duration: Long = 0L
        var order: Int = 0
        var index: Int = 0
        var playFromHistoryPosition: Boolean = true
        var imageBitmap: Bitmap? = null
        var extra: Bundle? = null
        var mediaType: String? = null
        var broadcastStartTime: String? = null
        var broadcastEndTime: String? = null
        var tags: String? = null
        var isLive: Boolean = false

        @Suppress("DEPRECATION")
        constructor(parcel: Parcel) : this() {
                mediaId = parcel.readString()
                channelId = parcel.readString()
                path = parcel.readString()
                cpId = parcel.readString()
                categoryId = parcel.readString()
                contentId = parcel.readString()
                albumId = parcel.readString()
                trackId = parcel.readString()
                title = parcel.readString()
                subTitle = parcel.readString()
                author = parcel.readString()
                imageUrl = parcel.readString()
                description = parcel.readString()
                intro = parcel.readString()
                duration = parcel.readLong()
                order = parcel.readInt()
                index = parcel.readInt()
                playFromHistoryPosition = parcel.readByte().toInt() != 0
                imageBitmap = parcel.readParcelable(Bitmap::class.java.classLoader)
                extra = parcel.readBundle(MediaInfo::class.java.classLoader)
                mediaType = parcel.readString()
                broadcastStartTime = parcel.readString()
                broadcastEndTime = parcel.readString()
                tags = parcel.readString()
                isLive = parcel.readByte().toInt() != 0
        }

        override fun writeToParcel(parcel: Parcel, flags: Int) {
                parcel.writeString(mediaId)
                parcel.writeString(channelId)
                parcel.writeString(path)
                parcel.writeString(cpId)
                parcel.writeString(categoryId)
                parcel.writeString(contentId)
                parcel.writeString(albumId)
                parcel.writeString(trackId)
                parcel.writeString(title)
                parcel.writeString(subTitle)
                parcel.writeString(author)
                parcel.writeString(imageUrl)
                parcel.writeString(description)
                parcel.writeString(intro)
                parcel.writeLong(duration)
                parcel.writeInt(order)
                parcel.writeInt(index)
                parcel.writeByte((if (playFromHistoryPosition) 1 else 0).toByte())
                parcel.writeParcelable(imageBitmap, flags)
                parcel.writeBundle(extra)
                parcel.writeString(mediaType)
                parcel.writeString(broadcastStartTime)
                parcel.writeString(broadcastEndTime)
                parcel.writeString(tags)
                parcel.writeByte((if (isLive) 1 else 0).toByte())
        }

        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<MediaInfo> {
                override fun createFromParcel(parcel: Parcel): MediaInfo = MediaInfo(parcel)

                override fun newArray(size: Int): Array<MediaInfo?> = arrayOfNulls(size)
        }
}
