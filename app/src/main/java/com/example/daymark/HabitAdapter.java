package com.example.daymark;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HabitAdapter extends RecyclerView.Adapter<HabitAdapter.ViewHolder> {
    public interface HabitActionListener {
        void onEdit(Habit habit);

        void onChanged();
    }

    private final Context context;
    private final DayMarkDbHelper dbHelper;
    private final HabitActionListener listener;
    private List<Habit> data = new ArrayList<>();

    public HabitAdapter(Context context, DayMarkDbHelper dbHelper, HabitActionListener listener) {
        this.context = context;
        this.dbHelper = dbHelper;
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submitList(List<Habit> habits) {
        data = habits == null ? new ArrayList<>() : habits;
        notifyDataSetChanged();
    }

    /** Current ids in display order, for persisting a manual drag reorder. */
    public List<Long> currentOrderIds() {
        List<Long> ids = new ArrayList<>(data.size());
        for (Habit habit : data) {
            ids.add(habit.id);
        }
        return ids;
    }

    /** Swap two rows during a drag and animate the move; persistence happens on drop. */
    public void onItemMove(int fromPosition, int toPosition) {
        if (fromPosition < 0 || toPosition < 0
                || fromPosition >= data.size() || toPosition >= data.size()) {
            return;
        }
        Collections.swap(data, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    @Override
    public long getItemId(int position) {
        return data.get(position).id;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_habit, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Habit habit = data.get(position);
        holder.titleText.setText(habit.title);
        holder.timeText.setText("时间：" + habit.timeText);
        holder.contentText.setText(habit.content);
        holder.statusText.setText(statusFor(habit));
        holder.metaText.setText("分类：" + habit.category +
                "  |  " + habit.frequencyLabel() +
                "  |  " + habit.streakLabel() +
                "  |  累计 " + habit.checkCount + " 次" +
                (TextUtils.isEmpty(habit.reminderTime) ? "" : "  |  提醒 " + habit.reminderTime));
        holder.noteText.setText(TextUtils.isEmpty(habit.lastNote) ? "最近备注：暂无" : "最近备注：" + habit.lastNote);

        if (habit.hasGoal()) {
            holder.goalText.setVisibility(View.VISIBLE);
            holder.goalProgress.setVisibility(View.VISIBLE);
            holder.goalText.setText(habit.goalReached()
                    ? "目标已达成：坚持 " + habit.targetDays + " 天 🎉"
                    : "目标进度：" + habit.totalDays + " / " + habit.targetDays + " 天（"
                            + habit.goalProgress() + "%）");
            holder.goalProgress.setProgress(habit.goalProgress());
        } else {
            holder.goalText.setVisibility(View.GONE);
            holder.goalProgress.setVisibility(View.GONE);
        }

        if (!TextUtils.isEmpty(habit.imageUri)) {
            holder.photoView.setVisibility(View.VISIBLE);
            // 150dp image slot; decode to about that size, off the main thread (see ImageLoader).
            int targetPx = (int) (150 * context.getResources().getDisplayMetrics().density);
            ImageLoader.load(holder.photoView, habit.imageUri, targetPx);
            holder.photoView.setOnClickListener(v -> {
                Intent intent = new Intent(context, ImagePreviewActivity.class);
                intent.putExtra("image_uri", habit.imageUri);
                context.startActivity(intent);
            });
        } else {
            holder.photoView.setVisibility(View.GONE);
            holder.photoView.setImageDrawable(null);
            holder.photoView.setTag(R.id.photoView, null);
            holder.photoView.setOnClickListener(null);
        }

        holder.doneButton.setText(habit.isCheckedToday() ? "再记" : "打卡");
        holder.doneButton.setOnClickListener(v -> showCheckDialog(habit));
        holder.undoButton.setVisibility(habit.isCheckedToday() ? View.VISIBLE : View.GONE);
        holder.undoButton.setOnClickListener(v -> confirmUndo(habit));
        holder.noteButton.setOnClickListener(v -> showNoteDialog(habit));
        holder.editButton.setOnClickListener(v -> listener.onEdit(habit));
        holder.deleteButton.setOnClickListener(v -> confirmDelete(habit));
    }

    /** Status line reflecting today's schedule: done, due, or not scheduled today. */
    private String statusFor(Habit habit) {
        if (habit.isCheckedToday()) {
            return "今日已完成";
        }
        if (!habit.isScheduledToday()) {
            return "今日无需打卡";
        }
        return "今日待完成";
    }

    private void showCheckDialog(Habit habit) {
        EditText input = buildNoteInput("今天完成了什么？可不填");
        new AlertDialog.Builder(context)
                .setTitle("打卡：" + habit.title)
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    String note = input.getText().toString().trim();
                    // DB write off the main thread; report back on the UI thread.
                    AppExecutors.io().execute(() -> {
                        boolean ok = dbHelper.markChecked(habit.id, note);
                        AppExecutors.main().execute(() -> {
                            if (ok) {
                                Toast.makeText(context, "打卡成功", Toast.LENGTH_SHORT).show();
                                listener.onChanged();
                            }
                        });
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showNoteDialog(Habit habit) {
        EditText input = buildNoteInput("补充一条打卡备注");
        new AlertDialog.Builder(context)
                .setTitle("打卡备注")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    String note = input.getText().toString().trim();
                    if (TextUtils.isEmpty(note)) {
                        Toast.makeText(context, "备注不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    AppExecutors.io().execute(() -> {
                        boolean ok = dbHelper.addNote(habit.id, note);
                        AppExecutors.main().execute(() -> {
                            if (ok) {
                                Toast.makeText(context, "备注已保存", Toast.LENGTH_SHORT).show();
                                listener.onChanged();
                            }
                        });
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private EditText buildNoteInput(String hint) {
        EditText input = new EditText(context);
        int padding = (int) (16 * context.getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding / 2, padding, padding / 2);
        input.setHint(hint);
        input.setMinLines(2);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        return input;
    }

    private void confirmUndo(Habit habit) {
        new AlertDialog.Builder(context)
                .setTitle("撤销今日打卡")
                .setMessage("将删除“" + habit.title + "”今天最近的一条打卡记录，确定吗？")
                .setPositiveButton("撤销", (dialog, which) -> {
                    AppExecutors.io().execute(() -> {
                        boolean ok = dbHelper.undoTodayCheck(habit.id);
                        AppExecutors.main().execute(() -> {
                            if (ok) {
                                Toast.makeText(context, "已撤销今日打卡", Toast.LENGTH_SHORT).show();
                                listener.onChanged();
                            } else {
                                Toast.makeText(context, "今天没有可撤销的打卡", Toast.LENGTH_SHORT).show();
                            }
                        });
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmDelete(Habit habit) {
        new AlertDialog.Builder(context)
                .setTitle("删除事件")
                .setMessage("确定删除“" + habit.title + "”吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    // Cancel the alarm on the main thread (cheap, no DB), delete off-thread.
                    ReminderReceiver.cancel(context, habit.id);
                    AppExecutors.io().execute(() -> {
                        dbHelper.deleteHabit(habit.id);
                        AppExecutors.main().execute(listener::onChanged);
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView titleText;
        final TextView timeText;
        final TextView metaText;
        final TextView contentText;
        final TextView noteText;
        final TextView statusText;
        final TextView goalText;
        final LinearProgressIndicator goalProgress;
        final ImageView photoView;
        final MaterialButton doneButton;
        final MaterialButton undoButton;
        final MaterialButton noteButton;
        final MaterialButton editButton;
        final MaterialButton deleteButton;

        ViewHolder(View view) {
            super(view);
            titleText = view.findViewById(R.id.titleText);
            timeText = view.findViewById(R.id.timeText);
            metaText = view.findViewById(R.id.metaText);
            contentText = view.findViewById(R.id.contentText);
            noteText = view.findViewById(R.id.noteText);
            statusText = view.findViewById(R.id.statusText);
            goalText = view.findViewById(R.id.goalText);
            goalProgress = view.findViewById(R.id.goalProgress);
            photoView = view.findViewById(R.id.photoView);
            doneButton = view.findViewById(R.id.doneButton);
            undoButton = view.findViewById(R.id.undoButton);
            noteButton = view.findViewById(R.id.noteButton);
            editButton = view.findViewById(R.id.editButton);
            deleteButton = view.findViewById(R.id.deleteButton);
        }
    }
}
