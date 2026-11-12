package cn.modificator.launcher.modelController;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;
import java.util.Observable;

/**
 * Created by Modificator
 * time: 2016-12-03 2:00 AM
 * des:create file and achieve model
 */

public class ObservableFloat extends Observable implements Parcelable, Serializable {
  static final long serialVersionUID = 1L;
  private float mValue;

  public ObservableFloat(float mValue) {
    this.mValue = mValue;
  }

  public ObservableFloat() {
  }

  public float get() {
    return mValue;
  }

  public void set(float value) {
    this.mValue = value;
    // Observable only notifies after setChanged(): calling it afterwards delayed every font
    // size change by one step (the first change after start did nothing).
    setChanged();
    notifyObservers(mValue);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeFloat(mValue);
  }

  public static final Parcelable.Creator<ObservableFloat> CREATOR
      = new Parcelable.Creator<ObservableFloat>() {

    @Override
    public ObservableFloat createFromParcel(Parcel source) {
      return new ObservableFloat(source.readFloat());
    }

    @Override
    public ObservableFloat[] newArray(int size) {
      return new ObservableFloat[size];
    }
  };
}
