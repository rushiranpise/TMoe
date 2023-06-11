package cc.ioctl.tmoe.hook.func;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import cc.ioctl.tmoe.hook.base.CommonDynamicHook;
import cc.ioctl.tmoe.td.AccountController;
import cc.ioctl.tmoe.util.HookUtils;
import cc.ioctl.tmoe.util.HostInfo;
import cc.ioctl.tmoe.util.Initiator;
import cc.ioctl.tmoe.util.Utils;
import de.robv.android.xposed.XposedHelpers;

public class AntiDeleteMsg extends CommonDynamicHook {
    public static final AntiDeleteMsg INSTANCE = new AntiDeleteMsg();

    private AntiDeleteMsg() {
    }

    private static final Map<Integer, SQLiteDatabase> mDatabase = new HashMap<>(1);

    private static final Object lock = new Object();


    private static SQLiteDatabase ensureDatabase(int slot) {
        if (!(slot >= 0 && slot < Short.MAX_VALUE)) {
            throw new IllegalArgumentException("invalid slot: " + slot);
        }
        if (mDatabase.containsKey(slot)) {
            return mDatabase.get(slot);
        }
        Context context = HostInfo.getApplication();
        File filesDir = context.getFilesDir();
        File databaseFile = new File(
                slot == 0 ? filesDir.getAbsolutePath() : new File(filesDir, "account" + slot).getAbsolutePath(),
                "TMoe_deleted_messages.db"
        );
        boolean createTable = !databaseFile.exists();
        SQLiteDatabase database = SQLiteDatabase.openDatabase(
                databaseFile.getAbsolutePath(),
                null,
                SQLiteDatabase.OPEN_READWRITE | (createTable ? SQLiteDatabase.CREATE_IF_NECESSARY : 0)
        );
        synchronized (lock) {
            database.beginTransaction();
            try {
                database.rawQuery("PRAGMA secure_delete = ON", null).close();
                database.rawQuery("PRAGMA temp_store = MEMORY", null).close();
                database.rawQuery("PRAGMA journal_mode = WAL", null).close();
                database.rawQuery("PRAGMA journal_size_limit = 10485760", null).close();
                database.rawQuery("PRAGMA busy_timeout = 5000", null).close();
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        }
        database.execSQL("CREATE TABLE IF NOT EXISTS t_deleted_messages (\n" +
                "  message_id INTEGER NOT NULL,\n" +
                "  dialog_id INTEGER NOT NULL\n" +
                ");");
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_messages_combined ON t_deleted_messages (message_id, dialog_id)");
        mDatabase.put(slot, database);
        return database;
    }


    public static boolean messageIsDeleted(int messageId, long dialogId) {
        int currentSlot = AccountController.getCurrentActiveSlot();
        if (currentSlot < 0) {
            Utils.logw("message_is_delete: no active account");
            return false;
        }
        SQLiteDatabase database = ensureDatabase(currentSlot);
        Cursor cursor = null;
        boolean result;

        try {
            String[] columns = {"message_id", "dialog_id"};
            String selection = "message_id = ? AND dialog_id = ?";
            String[] selectionArgs = {String.valueOf(messageId), String.valueOf(dialogId)};
            cursor = database.query("t_deleted_messages", columns, selection, selectionArgs, null, null, null);

            result = (cursor.getCount() > 0);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return result;
    }

    private void insertDeletedMessage(ArrayList<Integer> messageIds, long dialogId) {
        int currentSlot = AccountController.getCurrentActiveSlot();
        if (currentSlot < 0) {
            Utils.logw("message_is_delete: no active account");
            return;
        }
        SQLiteDatabase database = ensureDatabase(currentSlot);
        database.beginTransaction();
        try {
            for (Integer messageId : messageIds) {
                ContentValues values = new ContentValues();
                values.put("message_id", messageId);
                values.put("dialog_id", dialogId);

                database.insert("t_deleted_messages", null, values);
            }
            database.setTransactionSuccessful();
        } catch (Exception e) {
            if (!(e instanceof SQLiteConstraintException)) {
                Utils.loge("failed to insert deleted message: " + e.getMessage());
            }
        } finally {
            database.endTransaction();
        }
    }


    private static <T> ArrayList<T> castList(Object obj, Class<T> clazz) {
        ArrayList<T> result = new ArrayList<>();
        if (obj instanceof ArrayList<?>) {
            for (Object o : (ArrayList<?>) obj)
                result.add(clazz.cast(o));

            return result;
        }
        return null;
    }

    @Override
    public boolean initOnce() throws Exception {
        Class<?> messagesStorage = Initiator.loadClass("org.telegram.messenger.MessagesStorage");
        Class<?> notificationCenter = Initiator.loadClass("org.telegram.messenger.NotificationCenter");
        Class<?> notificationsController = Initiator.loadClass("org.telegram.messenger.NotificationsController");

        int messagesDeletedValue = (int) XposedHelpers.getStaticObjectField(notificationCenter, "messagesDeleted");


        Method postNotificationName = notificationCenter.getDeclaredMethod("postNotificationName", int.class, Object[].class);


        Method removeDeletedMessagesFromNotifications = null;
        for (Method method : notificationsController.getDeclaredMethods()) {
            if (method.getName().equals("removeDeletedMessagesFromNotifications")) {
                removeDeletedMessagesFromNotifications = method;
            }
        }

        ArrayList<Method> methods = new ArrayList<>();
        for (Method declaredMethod : messagesStorage.getDeclaredMethods()) {
            if (declaredMethod.getName().equals("markMessagesAsDeleted") || declaredMethod.getName().equals("updateDialogsWithDeletedMessages")) {
                methods.add(declaredMethod);
            }
        }

        for (Method method : methods) {
            HookUtils.hookBeforeIfEnabled(this, method, param -> {
                param.setResult(null);
            });
        }

        HookUtils.hookBeforeIfEnabled(this, postNotificationName, param -> {
            if ((int) param.args[0] == messagesDeletedValue) {
                Object[] args = (Object[]) param.args[1];
                long dialogID = (long) args[1];
                ArrayList<Integer> arrayList = castList(args[0], Integer.class);
                param.setResult(null);
                insertDeletedMessage(arrayList, dialogID);
            }
        });

        if (removeDeletedMessagesFromNotifications == null) {
            return false;
        }

        HookUtils.hookBeforeIfEnabled(this, removeDeletedMessagesFromNotifications, param -> {
            param.setResult(null);
        });

        return true;
    }
}