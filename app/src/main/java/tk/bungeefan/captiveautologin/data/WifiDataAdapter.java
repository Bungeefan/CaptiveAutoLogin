package tk.bungeefan.captiveautologin.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.List;

import tk.bungeefan.captiveautologin.R;

public class WifiDataAdapter<T> extends ArrayAdapter<T> {

    private final int resource;
    private final SharedPreferences prefs;

    public WifiDataAdapter(@NonNull Context context, int resource, @NonNull List<T> objects) {
        super(context, resource, objects);
        this.resource = resource;
        this.prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View view;
        if (convertView == null) {
            view = LayoutInflater.from(getContext()).inflate(resource, parent, false);
        } else {
            view = convertView;
        }
        WifiData wifiData = (WifiData) getItem(position);
        if (wifiData != null) {
            TextView ssidView = view.findViewById(R.id.text1);
            ssidView.setText(wifiData.getSSID());
            TextView usernameView = view.findViewById(R.id.text2);
            usernameView.setText(wifiData.getUsername());
            TextView passwordView = view.findViewById(R.id.text3);
            passwordView.setTransformationMethod(new PasswordTransformationMethod());
            passwordView.setText(wifiData.getPassword(prefs));
        }
        return view;
    }
}
