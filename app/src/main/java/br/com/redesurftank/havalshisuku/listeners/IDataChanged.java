package br.com.redesurftank.havalshisuku.listeners;
 
import androidx.annotation.Nullable;

public interface IDataChanged {
    void onDataChanged(String key, @Nullable String value);
}
