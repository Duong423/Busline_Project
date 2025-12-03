package com.busify.project.bus.dto.request;

import com.busify.project.bus.enums.BusStatus;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.*;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BusMGMTRequestDTO {

    @NotBlank(message = "Biển số xe không được để trống")
    @Pattern(
            regexp = "^[0-9]{2}[A-Z]-[0-9]{3}\\.[0-9]{2}$",
            message = "Biển số xe phải theo đúng định dạng: 88A-888.88"
    )
    private String licensePlate;

    @NotNull(message = "Mã mẫu xe không được trống")
    @Positive(message = "Mã mẫu xe phải là số nguyên")
    private Long modelId;

    @NotNull(message = "Mã nhà xe không được trống")
    @Positive(message = "Mã nhà xe phải là số nguyên")
    private Long operatorId;

    @NotNull(message = "Mã bố cục ghế không được trống")
    @Positive(message = "Mã bố cục ghế phải là số nguyên")
    private Integer seatLayoutId;

    @NotNull(message = "Tiện ích không được để trống")
    private String amenities;

    @NotNull(message = "Trạng thái xe không được để trống")
    private BusStatus status;

    // Thêm danh sách ảnh bus (upload nhiều ảnh)
    private List<MultipartFile> images;

    @JsonDeserialize(using = LongListDeserializer.class)
    private List<Long> deletedImageIds;  // Ids of images to delete

    // Custom deserializer to handle both JSON string and array
    public static class LongListDeserializer extends JsonDeserializer<List<Long>> {
        private static final ObjectMapper mapper = new ObjectMapper();

        @Override
        public List<Long> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String value = p.getText();
            
            // If empty or null, return empty list
            if (value == null || value.trim().isEmpty() || value.equals("null")) {
                return new ArrayList<>();
            }
            
            // If it's a JSON array string like "[1,2,3]", parse it
            if (value.startsWith("[") && value.endsWith("]")) {
                try {
                    return mapper.readValue(value, new TypeReference<List<Long>>() {});
                } catch (Exception e) {
                    return new ArrayList<>();
                }
            }
            
            // If it's already parsed as array by Jackson
            try {
                return p.readValueAs(new TypeReference<List<Long>>() {});
            } catch (Exception e) {
                return new ArrayList<>();
            }
        }
    }
}
