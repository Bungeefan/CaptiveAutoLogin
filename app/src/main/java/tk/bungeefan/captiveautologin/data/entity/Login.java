package tk.bungeefan.captiveautologin.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.io.Serializable;

@Entity(indices = {@Index(value = {"ssid"}, unique = true)})
public class Login implements Serializable {

    @ColumnInfo(name = "ssid")
    //public for field detection
    public String ssid;
    @PrimaryKey
    private Long id;
    @ColumnInfo(name = "username")
    private String username;

    @ColumnInfo(name = "password")
    private String password;

    @ColumnInfo(name = "last_login")
    private Long lastLogin;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSSID() {
        return ssid;
    }

    public void setSSID(String ssid) {
        this.ssid = ssid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Long getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(Long last_login) {
        this.lastLogin = last_login;
    }

    @NonNull
    @Override
    public String toString() {
        return ssid + " " + username;
    }

    public String toCSVString() {
        return ssid + ";" + (username != null ? username : "") + ";" + (password != null ? password : "") + ";" + (lastLogin != null ? lastLogin : "");
    }
}
