package tk.bungeefan.captiveautologin.data;

import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;
import java.util.function.Consumer;

import tk.bungeefan.captiveautologin.R;
import tk.bungeefan.captiveautologin.data.entity.Login;

public class LoginAdapter extends ListAdapter<Login, LoginAdapter.LoginViewHolder> {

    public static final DiffUtil.ItemCallback<Login> DIFF_CALLBACK = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull Login oldItem, @NonNull Login newItem) {
            return oldItem == newItem;
        }

        @Override
        public boolean areContentsTheSame(@NonNull Login oldItem, @NonNull Login newItem) {
            return Objects.equals(oldItem.getSSID(), newItem.getSSID()) && Objects.equals(oldItem.getUsername(), newItem.getUsername());
        }
    };
    private final Consumer<Login> clickListener;
    private int currentPos;

    public LoginAdapter(Consumer<Login> clickListener) {
        super(LoginAdapter.DIFF_CALLBACK);
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public LoginViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LoginViewHolder loginViewHolder = new LoginViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item, parent, false));
        loginViewHolder.itemView.setOnClickListener(v -> {
            clickListener.accept((Login) v.getTag());
        });
        loginViewHolder.itemView.setOnLongClickListener(v -> {
            currentPos = loginViewHolder.getAdapterPosition();
            return false;
        });
        return loginViewHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull LoginViewHolder holder, int position) {
        Login login = getItem(position);
        holder.itemView.setTag(login);
        holder.bindTo(login);
    }

    public int getCurrentPos() {
        return this.currentPos;
    }

    public Login getSelectedItem() {
        return getCurrentList().get(currentPos);
    }

    public static class LoginViewHolder extends RecyclerView.ViewHolder {

        private final TextView ssidView;
        private final TextView usernameView;
        private final TextView passwordView;

        LoginViewHolder(View itemView) {
            super(itemView);
            ssidView = itemView.findViewById(R.id.text1);
            usernameView = itemView.findViewById(R.id.text2);
            passwordView = itemView.findViewById(R.id.text3);
            passwordView.setTransformationMethod(new PasswordTransformationMethod());
        }

        public void bindTo(Login loginData) {
            ssidView.setText(loginData.getSSID());
            usernameView.setText(loginData.getUsername());
            passwordView.setText(loginData.getPassword());
        }
    }

}
