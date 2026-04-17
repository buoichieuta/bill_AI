package com.example.bill_ai;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.bill_ai.R;
import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    private RecyclerView rvChat;
    private ChatAdapter adapter;
    private List<ChatMessage> messages = new ArrayList<>();
    private EditText etMessage;
    private ImageButton btnSend;
    private LinearLayout layoutSuggestions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        rvChat = findViewById(R.id.rvChat);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSendMessage);
        layoutSuggestions = findViewById(R.id.layoutSuggestions);

        adapter = new ChatAdapter(messages);
        rvChat.setLayoutManager(new LinearLayoutManager(this));
        rvChat.setAdapter(adapter);

        findViewById(R.id.btnBackChat).setOnClickListener(v -> finish());

        btnSend.setOnClickListener(v -> {
            String text = etMessage.getText().toString().trim();
            if (!text.isEmpty()) {
                performSendMessage(text);
                etMessage.setText("");
            }
        });

        setupSuggestions();

        // Chào mừng
        addBotMessage("Xin chào! Tôi là trợ lý Bill AI. Tôi có sẵn một số gợi ý bên dưới, bạn có thể nhấn chọn hoặc chat trực tiếp với tôi nhé!");
    }

    private void setupSuggestions() {
        String[] suggestions = {
            "Cách quét hóa đơn", 
            "Lỗi nhận diện sai", 
            "Dữ liệu có an toàn?", 
            "Xuất file Excel",
            "Đổi mật khẩu"
        };

        for (String s : suggestions) {
            addSuggestionChip(s);
        }
    }

    private void addSuggestionChip(String text) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setPadding(32, 16, 32, 16);
        chip.setTextSize(12);
        chip.setTextColor(Color.parseColor("#3B82F6"));
        chip.setBackgroundResource(R.drawable.bg_search_rounded);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, 
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 16, 0);
        chip.setLayoutParams(params);

        chip.setOnClickListener(v -> performSendMessage(text));
        layoutSuggestions.addView(chip);
    }

    private void performSendMessage(String text) {
        messages.add(new ChatMessage(text, true));
        adapter.notifyItemInserted(messages.size() - 1);
        rvChat.scrollToPosition(messages.size() - 1);

        // Đóng gợi ý sau khi chọn (tùy chọn)
        // findViewById(R.id.hsvSuggestions).setVisibility(View.GONE);

        new Handler().postDelayed(() -> {
            String response = getBotResponse(text);
            addBotMessage(response);
        }, 1000);
    }

    private void addBotMessage(String text) {
        messages.add(new ChatMessage(text, false));
        adapter.notifyItemInserted(messages.size() - 1);
        rvChat.scrollToPosition(messages.size() - 1);
    }

    private String getBotResponse(String input) {
        String lower = input.toLowerCase();
        
        if (lower.contains("quét") || lower.contains("chụp")) {
            return "Để quét hóa đơn chuẩn:\n1. Nhấn nút Camera ở trang chính.\n2. Đặt hóa đơn phẳng, đủ ánh sáng.\n3. Đảm bảo lấy được toàn bộ 4 góc bill.\nChúc bạn thành công!";
        } 
        
        if (lower.contains("sai") || lower.contains("nhận diện")) {
            return "Nếu hóa đơn bị nhận diện sai thông tin, có thể do ảnh chụp mờ hoặc góc chụp không thẳng. Bạn hãy thử chụp lại nhé. Nếu vẫn chưa được, hãy gửi cho chúng tôi ở mục 'Gửi phản hồi'!";
        } 
        
        if (lower.contains("an toàn") || lower.contains("bảo mật") || lower.contains("dữ liệu")) {
            return "Bill AI sử dụng công nghệ bảo mật của Supabase. Toàn bộ dữ liệu của bạn được mã hóa và chỉ tài khoản của bạn mới có quyền truy cập. Bạn hoàn toàn yên tâm nhé!";
        } 

        if (lower.contains("excel") || lower.contains("xuất")) {
            return "Để xuất file Excel, bạn vào mục 'Hóa đơn', chọn các hóa đơn cần xuất và nhấn nút 'Xuất Excel' ở góc trên màn hình.";
        }

        if (lower.contains("mật khẩu") || lower.contains("đổi")) {
            return "Bạn có thể đổi mật khẩu trong phần Cài đặt > Đổi mật khẩu. Chúng tôi sẽ gửi mã OTP xác nhận về email của bạn.";
        }

        // Fallback response for unknown inputs
        return "Xin lỗi, tôi chưa hiểu rõ ý bạn lắm. Nếu bạn đang gặp vấn đề kỹ thuật hoặc cần hỗ trợ gấp, bạn có thể nhắn tin chi tiết hoặc sử dụng mục 'Gửi phản hồi' ở màn hình trước để đội ngũ của tôi hỗ trợ nhé!";
    }

    // --- Model Class ---
    private static class ChatMessage {
        String text;
        boolean isUser;
        ChatMessage(String text, boolean isUser) { this.text = text; this.isUser = isUser; }
    }

    // --- Adapter Class ---
    private class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {
        private final List<ChatMessage> list;

        ChatAdapter(List<ChatMessage> list) { this.list = list; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ChatMessage msg = list.get(position);
            holder.tv.setText(msg.text);
            
            if (msg.isUser) {
                holder.root.setGravity(Gravity.END);
            } else {
                holder.root.setGravity(Gravity.START);
            }

            float[] radii;
            if (msg.isUser) {
                radii = new float[]{32, 32, 32, 32, 0, 32, 32, 32};
                holder.tv.setTextColor(Color.WHITE);
            } else {
                radii = new float[]{32, 32, 32, 32, 32, 32, 0, 32};
                holder.tv.setTextColor(getResources().getColor(R.color.settings_text_main));
            }
            
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.RECTANGLE);
            shape.setCornerRadii(radii);
            shape.setColor(msg.isUser ? 0xFF22C55E : getResources().getColor(R.color.settings_card_bg));
            
            holder.tv.setBackground(shape);
            holder.tv.setPadding(32, 20, 32, 20);
        }

        @Override public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tv;
            LinearLayout root;
            ViewHolder(View v) { 
                super(v); 
                tv = v.findViewById(R.id.tvChatMessage); 
                root = v.findViewById(R.id.layoutChatItem);
            }
        }
    }
}
