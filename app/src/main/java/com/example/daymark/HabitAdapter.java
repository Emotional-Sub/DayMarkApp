package com.example.daymark;

import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HabitAdapter extends BaseAdapter {
    public interface HabitActionListener {
        void onEdit(Habit habit);

        void onChanged();
    }

    private final Context context;
    private final DayMarkDbHelper dbHelper;
    private final HabitActionListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
    private List<Habit> data = new ArrayList<>();

    public HabitAdapter(Context context, DayMarkDbHelper dbHelper, HabitActionListener listener) {
        this.context = context;
        this.dbHelper = dbHelper;
        this.listener = listener;
    }

    public void submitList(List<Habit> habits) {
        data = habits;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public Habit getItem(int position) {
        return data.get(position);
    }

    @Override
    public long getItemId(int position) {
        return data.get(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_habit, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Habit habit = getItem(position);
        holder.titleText.setText(habit.title);
        holder.timeText.setText("时间：" + habit.timeText);
        holder.contentText.setText(habit.content);
        holder.statusText.setText("已打卡 " + habit.checkCount + " 次");

        if (!TextUtils.isEmpty(habit.imageUri)) {
            holder.photoView.setVisibility(View.VISIBLE);
            holder.photoView.setImageURI(Uri.parse(habit.imageUri));
        } else {
            holder.photoView.setVisibility(View.GONE);
            holder.photoView.setImageDrawable(null);
        }

        holder.doneButton.setText(habit.lastCheckAt > 0
                ? "打卡\n" + dateFormat.format(new Date(habit.lastCheckAt))
                : "打卡");
        holder.doneButton.setOnClickListener(v -> {
            if (dbHelper.markChecked(habit.id)) {
                Toast.makeText(context, "打卡成功", Toast.LENGTH_SHORT).show();
                listener.onChanged();
            }
        });
        holder.editButton.setOnClickListener(v -> listener.onEdit(habit));
        holder.deleteButton.setOnClickListener(v -> confirmDelete(habit));
        return convertView;
    }

    private void confirmDelete(Habit habit) {
        new AlertDialog.Builder(context)
                .setTitle("删除事件")
                .setMessage("确定删除“" + habit.title + "”吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    dbHelper.deleteHabit(habit.id);
                    listener.onChanged();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static class ViewHolder {
        final TextView titleText;
        final TextView timeText;
        final TextView contentText;
        final TextView statusText;
        final ImageView photoView;
        final Button doneButton;
        final Button editButton;
        final Button deleteButton;

        ViewHolder(View view) {
            titleText = view.findViewById(R.id.titleText);
            timeText = view.findViewById(R.id.timeText);
            contentText = view.findViewById(R.id.contentText);
            statusText = view.findViewById(R.id.statusText);
            photoView = view.findViewById(R.id.photoView);
            doneButton = view.findViewById(R.id.doneButton);
            editButton = view.findViewById(R.id.editButton);
            deleteButton = view.findViewById(R.id.deleteButton);
        }
    }
}
