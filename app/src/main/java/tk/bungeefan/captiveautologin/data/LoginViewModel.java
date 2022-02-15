package tk.bungeefan.captiveautologin.data;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

import tk.bungeefan.captiveautologin.data.entity.Login;

public class LoginViewModel extends AndroidViewModel {

    private final AppDatabase db;
    private final LiveData<List<Login>> mAllLogins;
    private final LoginDao mLoginDao;

    public LoginViewModel(@NonNull Application application) {
        super(application);
        db = AppDatabase.getInstance(application);
        mLoginDao = db.loginDao();
        mAllLogins = mLoginDao.observeAll();
    }

    public AppDatabase getDatabase() {
        return db;
    }

    public LiveData<List<Login>> getAllLogins() {
        return mAllLogins;
    }

}
