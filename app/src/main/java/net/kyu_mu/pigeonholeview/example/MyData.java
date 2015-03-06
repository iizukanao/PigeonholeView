package net.kyu_mu.pigeonholeview.example;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by nao on 3/6/15.
 */
public class MyData implements Parcelable {
    private String name;

    // NOTE: In general, you should not save resource id to storage
    // since its value may change upon each compilation. Instead,
    // use context.getResources().getResourceName(resourceId)
    private int imageResourceId;

    private int viewPosition;

    public MyData(String name, int imageResourceId, int viewPosition) {
        this.name = name;
        this.imageResourceId = imageResourceId;
        this.viewPosition = viewPosition;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getImageResourceId() {
        return imageResourceId;
    }

    public void setImageResourceId(int imageResourceId) {
        this.imageResourceId = imageResourceId;
    }

    public int getViewPosition() {
        return viewPosition;
    }

    public void setViewPosition(int viewPosition) {
        this.viewPosition = viewPosition;
    }

    // Methods for Parcelable

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeInt(imageResourceId);
        dest.writeInt(viewPosition);
    }

    public static final Creator<MyData> CREATOR = new Creator<MyData>() {
        @Override
        public MyData createFromParcel(Parcel src) {
            return new MyData(src);
        }

        @Override
        public MyData[] newArray(int size) {
            return new MyData[size];
        }
    };

    public MyData(Parcel src) {
        name = src.readString();
        imageResourceId = src.readInt();
        viewPosition = src.readInt();
    }
}
