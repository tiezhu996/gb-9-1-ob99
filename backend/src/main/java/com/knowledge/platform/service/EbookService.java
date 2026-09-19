package com.knowledge.platform.service;

import com.knowledge.platform.dto.ApiResponse;
import com.knowledge.platform.dto.EbookCreateRequest;
import com.knowledge.platform.dto.EbookPageResponse;
import com.knowledge.platform.entity.Ebook;
import com.knowledge.platform.entity.Order;
import com.knowledge.platform.repository.EbookRepository;
import com.knowledge.platform.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class EbookService {
    @Autowired
    private EbookRepository ebookRepository;

    @Autowired
    private OrderRepository orderRepository;

    public ApiResponse<Page<Ebook>> list(Pageable pageable) {
        Page<Ebook> ebooks = ebookRepository.findByStatus(Ebook.Status.PUBLISHED, pageable);
        return ApiResponse.success(ebooks);
    }

    public ApiResponse<Ebook> getById(String id) {
        Optional<Ebook> ebookOpt = ebookRepository.findById(id);
        if (ebookOpt.isEmpty()) {
            return ApiResponse.error("电子书不存在");
        }
        Ebook ebook = ebookOpt.get();
        // 草稿和已下架电子书不允许通过详情入口直接访问（已购读者的阅读入口走阅读接口授权）
        if (ebook.getStatus() == Ebook.Status.DRAFT) {
            return ApiResponse.error("电子书不存在");
        }
        return ApiResponse.success(ebook);
    }

    /**
     * 计算试读最后一页（页码从 1 开始，含该页）。
     * 严格按全书实际页数与配置的试读比例计算：floor(total * percent)，
     * 至少开放 1 页；四舍五入/向上取整都可能让未购买读者读到超过 10% 的内容，故不采用。
     */
    public int calcSampleEndPage(Ebook ebook) {
        List<String> pages = ebook.getPages();
        int total = (pages != null && !pages.isEmpty()) ? pages.size()
                : (ebook.getPageCount() != null ? ebook.getPageCount() : 0);
        if (total <= 0) {
            return 0;
        }
        double percent = ebook.getSampleEndPercent() != null ? ebook.getSampleEndPercent() : 0.1;
        int sample = (int) Math.floor(total * percent);
        return Math.max(1, Math.min(sample, total));
    }

    private boolean hasPurchased(String userId, String ebookId) {
        if (userId == null) {
            return false;
        }
        return orderRepository.findFirstByUserIdAndItemIdAndTypeAndStatusIn(
                userId, ebookId, Order.OrderType.EBOOK_PURCHASE,
                new Order.Status[]{Order.Status.PAID}).isPresent();
    }

    /**
     * 阅读入口的授权状态查询：返回总页数、试读边界页、是否已购。
     * 不返回任何正文内容，供阅读器初始化和直接访问阅读入口时判定。
     */
    public ApiResponse<EbookPageResponse> getAccessStatus(String userId, String ebookId) {
        Optional<Ebook> ebookOpt = ebookRepository.findById(ebookId);
        if (ebookOpt.isEmpty()) {
            return ApiResponse.error("电子书不存在");
        }
        Ebook ebook = ebookOpt.get();
        boolean purchased = hasPurchased(userId, ebookId);

        if (ebook.getStatus() == Ebook.Status.DRAFT) {
            return ApiResponse.error("电子书不存在");
        }
        if (ebook.getStatus() == Ebook.Status.OFFLINE && !purchased) {
            // 作者下架后，未购买读者不能再进入
            return ApiResponse.error("该电子书已被作者下架");
        }

        EbookPageResponse response = new EbookPageResponse();
        response.setEbookId(ebook.getId());
        response.setTitle(ebook.getTitle());
        List<String> pages = ebook.getPages();
        response.setTotalPages(pages != null && !pages.isEmpty() ? pages.size() : ebook.getPageCount());
        response.setSampleEndPage(calcSampleEndPage(ebook));
        response.setPurchased(purchased);
        response.setLocked(true);
        return ApiResponse.success(response);
    }

    /**
     * 分页获取电子书正文，边界全部在服务端判定：
     * - 未购买：只能访问 1..sampleEndPage；翻页、重新打开、直接访问阅读入口都无法越过；
     * - 已购买：可访问全书（即使作者已下架）；
     * - 作者下架后未购买：拒绝访问。
     */
    public ApiResponse<EbookPageResponse> getPage(String userId, String ebookId, int page) {
        Optional<Ebook> ebookOpt = ebookRepository.findById(ebookId);
        if (ebookOpt.isEmpty()) {
            return ApiResponse.error("电子书不存在");
        }
        Ebook ebook = ebookOpt.get();
        List<String> pages = ebook.getPages() != null ? ebook.getPages() : new ArrayList<>();
        int totalPages = pages.isEmpty() ? (ebook.getPageCount() != null ? ebook.getPageCount() : 0) : pages.size();

        EbookPageResponse response = new EbookPageResponse();
        response.setEbookId(ebook.getId());
        response.setTitle(ebook.getTitle());
        response.setTotalPages(totalPages);
        response.setSampleEndPage(calcSampleEndPage(ebook));
        response.setPage(Math.max(1, page));
        response.setPurchased(hasPurchased(userId, ebookId));

        if (ebook.getStatus() == Ebook.Status.DRAFT) {
            return ApiResponse.error("电子书不存在");
        }
        if (ebook.getStatus() == Ebook.Status.OFFLINE && !response.isPurchased()) {
            response.setLocked(true);
            response.setMessage("该电子书已被作者下架，无法继续试读");
            return ApiResponse.error("该电子书已被作者下架", response);
        }
        if (page < 1 || page > totalPages) {
            response.setLocked(true);
            response.setMessage("页码超出范围");
            return ApiResponse.error("页码超出范围", response);
        }
        if (!response.isPurchased() && page > response.getSampleEndPage()) {
            // 越过试读边界：无论翻页还是直接访问 URL，一律不返回正文
            response.setLocked(true);
            response.setMessage("试读已结束，购买后可阅读完整内容");
            return ApiResponse.error("试读已结束，购买后可阅读完整内容", response);
        }

        response.setLocked(false);
        response.setContent(pages.isEmpty() ? "" : pages.get(page - 1));
        return ApiResponse.success(response);
    }

    @org.springframework.transaction.annotation.Transactional
    public ApiResponse<Ebook> create(String userId, EbookCreateRequest request) {
        Ebook ebook = new Ebook();
        ebook.setCreatorId(userId);
        ebook.setTitle(request.getTitle().trim());
        ebook.setDescription(request.getDescription());
        ebook.setPrice(request.getPrice());
        ebook.setFileType(Ebook.FileType.valueOf(request.getFileType().toUpperCase()));

        // 以实际提交的正文为准计算页数与字数
        List<String> pages = request.getPages() != null ? request.getPages() : new ArrayList<>();
        ebook.setPages(pages);
        ebook.setPageCount(pages.size());
        int wordCount = pages.stream().mapToInt(p -> p == null ? 0 : p.length()).sum();
        ebook.setWordCount(wordCount);

        if (request.getSampleEndPercent() != null) {
            double percent = request.getSampleEndPercent();
            if (percent < 0 || percent > 1) {
                return ApiResponse.error("试读比例必须在 0~1 之间");
            }
            ebook.setSampleEndPercent(percent);
        }

        LocalDateTime now = LocalDateTime.now();
        ebook.setCreatedAt(now);
        ebook.setUpdatedAt(now);
        ebook = ebookRepository.save(ebook);
        return ApiResponse.success("创建成功", ebook);
    }

    @org.springframework.transaction.annotation.Transactional
    public ApiResponse<Ebook> publish(String userId, String id) {
        Optional<Ebook> ebookOpt = ebookRepository.findById(id);
        if (ebookOpt.isEmpty() || !ebookOpt.get().getCreatorId().equals(userId)) {
            return ApiResponse.error("电子书不存在");
        }
        Ebook ebook = ebookOpt.get();
        if (ebook.getPages() == null || ebook.getPages().isEmpty()) {
            return ApiResponse.error("电子书没有正文内容，无法发布");
        }
        ebook.setStatus(Ebook.Status.PUBLISHED);
        ebook.setUpdatedAt(LocalDateTime.now());
        return ApiResponse.success(ebookRepository.save(ebook));
    }

    /**
     * 作者下架电子书。只影响新购买和未购买读者的访问，
     * 已存在的已支付订单继续有效，已购读者仍可阅读全文。
     */
    @org.springframework.transaction.annotation.Transactional
    public ApiResponse<Ebook> offline(String userId, String id) {
        Optional<Ebook> ebookOpt = ebookRepository.findById(id);
        if (ebookOpt.isEmpty() || !ebookOpt.get().getCreatorId().equals(userId)) {
            return ApiResponse.error("电子书不存在");
        }
        Ebook ebook = ebookOpt.get();
        ebook.setStatus(Ebook.Status.OFFLINE);
        ebook.setUpdatedAt(LocalDateTime.now());
        return ApiResponse.success("已下架", ebookRepository.save(ebook));
    }
}
