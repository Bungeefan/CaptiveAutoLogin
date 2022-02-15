package tk.bungeefan.captiveautologin;

import android.net.CaptivePortal;

import tk.bungeefan.captiveautologin.data.entity.Login;

public interface ILoginFailed {
    void loginFailed(CaptivePortal captivePortal, Login loginData, String response, String url);
}
