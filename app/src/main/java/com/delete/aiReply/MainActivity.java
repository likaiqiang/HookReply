package com.delete.aiReply;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.aiReply.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {

    private EditText editEndPoint;
    private EditText editApiKey;
    private EditText editModelName;
    private CheckBox checkboxLayoutViewer;
    private Button buttonSave;
    private Button buttonImportJson;

    // 定义SharedPreferences常量
    public static final String PREFS_NAME = "AiReplyPrefs";
    public static final String KEY_END_POINT = "end_point";
    public static final String KEY_API_KEY = "api_key";
    public static final String KEY_MODEL_NAME = "model_name";
    public static final String KEY_SHOW_LAYOUT_VIEWER = "show_layout_viewer";

    // 文件选择器的ActivityResultLauncher
    private ActivityResultLauncher<String[]> filePickerLauncher;

    private String lastPrompt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化视图
        editEndPoint = findViewById(R.id.editEndPoint);
        editApiKey = findViewById(R.id.editApiKey);
        editModelName = findViewById(R.id.editModelName);
        checkboxLayoutViewer = findViewById(R.id.checkboxLayoutViewer);
        buttonSave = findViewById(R.id.buttonSave);
        buttonImportJson = findViewById(R.id.buttonImportJson);

        // 注册文件选择器结果
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::processSelectedJsonFile
        );

        // 加载保存的设置
        try {
            loadSettings();
        } catch (IOException | JSONException e) {
            throw new RuntimeException(e);
        }

        // 设置保存按钮点击事件
        buttonSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        });

        // 设置导入JSON按钮点击事件
        buttonImportJson.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openJsonFilePicker();
            }
        });
    }

    // 打开文件选择器
    private void openJsonFilePicker() {
        filePickerLauncher.launch(new String[]{"application/json"});
    }

    // 处理选择的JSON文件
    private void processSelectedJsonFile(Uri uri) {
        if (uri == null) {
            Toast.makeText(this, "未选择文件", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String jsonContent = readTextFromUri(uri);
            importSettingsFromJson(jsonContent);
        } catch (IOException e) {
            Toast.makeText(this, "读取文件失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            Toast.makeText(this, "解析JSON失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // 从Uri读取文本内容
    private String readTextFromUri(Uri uri) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
        }
        return stringBuilder.toString();
    }

    // 从JSON字符串导入设置
    private void importSettingsFromJson(String jsonString) throws JSONException {
        JSONObject json = new JSONObject(jsonString);

        if (json.has(KEY_END_POINT)) {
            String baseUrl = json.getString(KEY_END_POINT);
            editEndPoint.setText(baseUrl);
        }

        if (json.has(KEY_API_KEY)) {
            String apiKey = json.getString(KEY_API_KEY);
            editApiKey.setText(apiKey);
        }

        if (json.has(KEY_MODEL_NAME)) {
            String apiKey = json.getString(KEY_MODEL_NAME);
            editModelName.setText(apiKey);
        }

        if (json.has(KEY_SHOW_LAYOUT_VIEWER)) {
            boolean showLayoutViewer = json.getBoolean(KEY_SHOW_LAYOUT_VIEWER);
            checkboxLayoutViewer.setChecked(showLayoutViewer);
        }

        Toast.makeText(this, "已从JSON导入设置", Toast.LENGTH_SHORT).show();
    }

    // 加载已保存的设置
    private void loadSettings() throws IOException, JSONException {

        String filePath = "/storage/emulated/0/Android/data/com.delete.aiReply/files/config.json";
        File configFile = new File(filePath);
        if (!configFile.exists() || !configFile.canRead()) {
            Log.d("MainActivity","AI Reply: 配置文件不存在或不可读: " + filePath);
            return;
        }
        StringBuilder content = new StringBuilder();
        BufferedReader reader = new BufferedReader(new FileReader(configFile));
        String line;
        while ((line = reader.readLine()) != null) {
            content.append(line);
        }
        reader.close();

        JSONObject jsonConfig = new JSONObject(content.toString());
        String endpoint = jsonConfig.optString(MainActivity.KEY_END_POINT, "");
        String apiKey = jsonConfig.optString(MainActivity.KEY_API_KEY, "");
        String modelName = jsonConfig.optString(MainActivity.KEY_MODEL_NAME, "");
        boolean showLayoutViewer = jsonConfig.optBoolean(MainActivity.KEY_SHOW_LAYOUT_VIEWER, false);

        editEndPoint.setText(endpoint);
        editApiKey.setText(apiKey);
        editModelName.setText(modelName);
        checkboxLayoutViewer.setChecked(showLayoutViewer);
    }

    // 保存设置
    private void saveSettings() {
        String endPoint = editEndPoint.getText().toString().trim();
        String apiKey = editApiKey.getText().toString().trim();
        String modelName = editModelName.getText().toString().trim();
        boolean showLayoutViewer = checkboxLayoutViewer.isChecked();

        // 简单的验证
        if (endPoint.isEmpty()) {
            Toast.makeText(this, "请输入endPoint", Toast.LENGTH_SHORT).show();
            return;
        }

        if (apiKey.isEmpty()) {
            Toast.makeText(this, "请输入apiKey", Toast.LENGTH_SHORT).show();
            return;
        }
        if (modelName.isEmpty()) {
            Toast.makeText(this, "请输入modelName", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject jsonConfig = new JSONObject();
            jsonConfig.put(KEY_END_POINT, endPoint);
            jsonConfig.put(KEY_API_KEY, apiKey);
            jsonConfig.put(KEY_MODEL_NAME, modelName);
            jsonConfig.put(KEY_SHOW_LAYOUT_VIEWER, showLayoutViewer);

            String filePath = getExternalFilesDir(null) + "/config.json";
            File configFile = new File(filePath);

            FileWriter writer = new FileWriter(configFile);
            writer.write(jsonConfig.toString());
            writer.close();

            // 确保文件有正确的权限
            configFile.setReadable(true, false);

            Toast.makeText(this, "设置已保存到: " + filePath, Toast.LENGTH_SHORT).show();
            Log.d("MainActivity", "Settings saved to: " + filePath);
        } catch (Exception e) {
            Log.d("MainActivity","AI Reply: 保存配置文件失败: " + e.getMessage());
        }
    }
}