package net.kyu_mu.pigeonholeview.example;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;

/**
 * Created by nao on 3/6/15.
 */
public class MyDataList extends ArrayList<MyData> implements Parcelable {
    public MyDataList() {
        super();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeList(this);
    }

    public static final Creator<MyDataList> CREATOR = new Creator<MyDataList>() {
        @Override
        public MyDataList createFromParcel(Parcel src) {
            return new MyDataList(src);
        }

        @Override
        public MyDataList[] newArray(int size) {
            return new MyDataList[size];
        }
    };

    private MyDataList(Parcel src) {
        src.readList(this, null);
    }
}
