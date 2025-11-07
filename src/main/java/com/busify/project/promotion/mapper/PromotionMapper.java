package com.busify.project.promotion.mapper;

import com.busify.project.promotion.dto.request.PromotionRequesDTO;
import com.busify.project.promotion.dto.response.PromotionResponseDTO;
import com.busify.project.promotion.entity.Promotion;
import com.busify.project.promotion.repository.UserPromotionRepository;

public class PromotionMapper {
    public static PromotionResponseDTO convertToDTO(Promotion promotion) {
        return convertToDTO(promotion, null);
    }

    public static PromotionResponseDTO convertToDTO(Promotion promotion, UserPromotionRepository userPromotionRepository) {
        if (promotion == null) {
            return null;
        }
        PromotionResponseDTO dto = new PromotionResponseDTO();
        dto.setId(promotion.getPromotionId());
        dto.setCode(promotion.getCode());
        dto.setDiscountType(promotion.getDiscountType());
        dto.setPromotionType(promotion.getPromotionType());
        dto.setDiscountValue(promotion.getDiscountValue());
        dto.setMinOrderValue(promotion.getMinOrderValue());
        dto.setStatus(promotion.getStatus());
        dto.setStartDate(promotion.getStartDate());
        dto.setEndDate(promotion.getEndDate());
        dto.setUsageLimit(promotion.getUsageLimit());
        dto.setPriority(promotion.getPriority());
        
        // Calculate used and remaining count if repository is provided
        if (userPromotionRepository != null) {
            long usedCount = userPromotionRepository.countUsedByPromotionId(promotion.getPromotionId());
            dto.setUsedCount(usedCount);
            
            // Calculate remaining count only if usageLimit is set
            if (promotion.getUsageLimit() != null && promotion.getUsageLimit() > 0) {
                long remaining = Math.max(0, promotion.getUsageLimit() - usedCount);
                dto.setRemainingCount(remaining);
            } else {
                dto.setRemainingCount(null); // Unlimited
            }
        }
        
        dto.setConditions(promotion.getConditions().stream()
                .map(PromotionConditionMapper::toResponseDTO)
                .toList());
        dto.setCampaignId(
                promotion.getCampaign() != null && promotion.getCampaign().getCampaignId() != null
                        ? promotion.getCampaign().getCampaignId()
                        : null);
        return dto;
    }

    public static Promotion convertToEntity(PromotionRequesDTO dto) {
        if (dto == null) {
            return null;
        }
        Promotion promotion = new Promotion();
        promotion.setCode(dto.getCode());
        promotion.setPromotionType(dto.getPromotionType());
        promotion.setDiscountType(dto.getDiscountType());
        promotion.setDiscountValue(dto.getDiscountValue());
        promotion.setMinOrderValue(dto.getMinOrderValue());
        promotion.setStatus(dto.getStatus());
        promotion.setStartDate(dto.getStartDate());
        promotion.setEndDate(dto.getEndDate());
        promotion.setUsageLimit(dto.getUsageLimit());
        promotion.setPriority(dto.getPriority());
        return promotion;
    }
}
