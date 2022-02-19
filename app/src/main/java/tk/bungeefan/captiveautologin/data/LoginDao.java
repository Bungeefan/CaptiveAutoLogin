package tk.bungeefan.captiveautologin.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import tk.bungeefan.captiveautologin.data.entity.Login;

@Dao
public interface LoginDao {

    @Query("SELECT * FROM login ORDER BY last_login DESC, SSID ASC")
    LiveData<List<Login>> observeAll();

    @Query("SELECT * FROM login")
    Single<List<Login>> loadAll();

    @Query("SELECT * FROM login WHERE id IN (:ids)")
    Single<List<Login>> loadAllByIds(long[] ids);

    @Query("SELECT * FROM login WHERE id =:id")
    Single<Login> findById(long id);

    @Query("SELECT * FROM login WHERE ssid LIKE :ssid")
    Single<List<Login>> findBySSID(String ssid);

    @Query("SELECT * FROM login WHERE ssid LIKE '%' || :search || '%' OR username LIKE '%' || :search || '%'")
    Single<List<Login>> findByName(String search);

    @Query("UPDATE login SET ssid = :ssid AND username = :username WHERE id = :id")
    Completable updateCredentials(long id, String ssid, String username);

    @Update
    Completable update(Login login);

    @Query("UPDATE login SET last_login = :lastLogin WHERE id = :id")
    Completable updateLastLogin(long id, Long lastLogin);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertAll(Login... logins);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertAll(List<Login> logins);

    @Delete
    Completable delete(Login login);

    @Query("DELETE FROM login")
    Completable deleteAll();

}

