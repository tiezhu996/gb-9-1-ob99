package com.knowledge.platform.service;

import com.knowledge.platform.dto.ApiResponse;
import com.knowledge.platform.dto.EbookCreateRequest;
import com.knowledge.platform.dto.PageContentResponse;
import com.knowledge.platform.dto.ReaderSessionResponse;
import com.knowledge.platform.entity.Ebook;
import com.knowledge.platform.entity.Order;
import com.knowledge.platform.entity.ReadingProgress;
import com.knowledge.platform.repository.CreatorRepository;
import com.knowledge.platform.repository.EbookRepository;
import com.knowledge.platform.repository.OrderRepository;
import com.knowledge.platform.repository.ReadingProgressRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class EbookService {

    private final EbookRepository ebookRepository;
    private final OrderRepository orderRepository;
    private final ReadingProgressRepository readingProgressRepository;
    private final CreatorRepository creatorRepository;

    @Autowired
    public EbookService(EbookRepository ebookRepository,
                        OrderRepository orderRepository,
                        ReadingProgressRepository readingProgressRepository,
                        CreatorRepository creatorRepository) {
        this.ebookRepository = ebookRepository;
        this.orderRepository = orderRepository;
        this.readingProgressRepository = readingProgressRepository;
        this.creatorRepository = creatorRepository;
    }

    public ApiResponse<Page<Ebook>> list(Pageable pageable) {
        return ApiResponse.success(ebookRepository.findByStatus(Ebook.Status.PUBLISHED, pageable));
    }

    public ApiResponse<List<Ebook>> listMine(String userId) {
        if (userId == null) {
            return ApiResponse.error("请先登录");
        }
        return ApiResponse.success(ebookRepository.findByCreatorId(userId));
    }

    public ApiResponse<Ebook> getById(String id) {
        Optional<Ebook> ebookOpt = ebookRepository.findById(id);
        if (ebookOpt.isEmpty()) {
            return ApiResponse.error("电子书不存在");
        }
        return ApiResponse.success(ebookOpt.get());
    }

    public ApiResponse<Ebook> create(String userId, EbookCreateRequest request) {
        if (userId == null) {
            return ApiResponse.error("请先登录");
        }
        if (!creatorRepository.existsByUserId(userId)) {
            return ApiResponse.error("只有创作者可以发布电子书");
        }

        List<String> pages = request.getContentPages() == null
                ? new ArrayList<>() : request.getContentPages();

        Ebook ebook = new Ebook();
        ebook.setCreatorId(userId);
        ebook.setTitle(request.getTitle());
        ebook.setDescription(request.getDescription());
        ebook.setPrice(request.getPrice());
        ebook.setFileType(request.getFileType() == null ? Ebook.FileType.PDF : request.getFileType());
        ebook.setFileUrl(request.getFileUrl());
        ebook.setContentPages(pages);
        ebook.setPageCount(pages.size());
        ebook.setWordCount(pages.stream().mapToInt(p -> p == null ? 0 : p.length()).sum());
        ebook.setSampleEndPercent(0.1);
        ebook.setStatus(Ebook.Status.PUBLISHED);
        LocalDateTime now = LocalDateTime.now();
        ebook.setCreatedAt(now);
        ebook.setUpdatedAt(now);

        return ApiResponse.success("电子书创建成功", ebookRepository.save(ebook));
    }

    /**
     * 作者下架电子书：已购读者的授权不受影响，新购买被拒绝（见 OrderService）。
     */
    public ApiResponse<Ebook> offline(String userId, String ebookId) {
        if (userId == null) {
            return ApiResponse.error("请先登录");
        }
        Optional<Ebook> ebookOpt = ebookRepository.findById(ebookId);
        if (ebookOpt.isEmpty()) {
            return ApiResponse.error("电子书不存在");
        }
        Ebook ebook = ebookOpt.get();
        if (!userId.equals(ebook.getCreatorId())) {
            return ApiResponse.error("只能下架自己的电子书");
        }
        ebook.setStatus(Ebook.Status.OFFLINE);
        ebook.setUpdatedAt(LocalDateTime.now());
        return ApiResponse.success("已下架", ebookRepository.save(ebook));
    }

    /**
     * 阅读器会话：试读范围完全按全书实际页数计算，未购买者的起始页不能越过边界。
     */
    public ApiResponse<ReaderSessionResponse> openReader(String userId, String ebookId) {
        Optional<Ebook> ebookOpt = ebookRepository.findById(ebookId);
        if (ebookOpt.isEmpty()) {
            return ApiResponse.error("电子书不存在");
        }
        Ebook ebook = ebookOpt.get();

        boolean purchased = userId != null && hasPurchased(userId, ebookId);

        // 未购买读者：草稿/已下架书籍不开放任何阅读入口
        if (!purchased && ebook.getStatus() != Ebook.Status.PUBLISHED) {
            return ApiResponse.error("该电子书已下架");
        }

        int pageCount = totalPages(ebook);
        int sampleEndPage = sampleEndPage(ebook);

        int currentPage = 1;
        if (userId != null) {
            Optional<ReadingProgress> progressOpt =
                    readingProgressRepository.findByUserIdAndEbookId(userId, ebookId);
            if (progressOpt.isPresent()) {
                int saved = progressOpt.get().getCurrentPage() == null ? 1
                        : progressOpt.get().getCurrentPage();
                // 重新打开时按授权强制收敛：未购买者即使保存过越界页码也会被拉回试读区
                currentPage = Math.min(Math.max(saved, 1), purchased ? Math.max(pageCount, 1) : sampleEndPage);
            }
        }

        ReaderSessionResponse response = new ReaderSessionResponse();
        response.setEbookId(ebook.getId());
        response.setTitle(ebook.getTitle());
        response.setPageCount(pageCount);
        response.setSampleEndPage(sampleEndPage);
        response.setPurchased(purchased);
        response.setOffShelf(ebook.getStatus() == Ebook.Status.OFFLINE);
        response.setSampleEndPercent(ebook.getSampleEndPercent() == null ? 0.1 : ebook.getSampleEndPercent());
        response.setCurrentPage(currentPage);
        return ApiResponse.success(response);
    }

    /**
     * 分页取正文。边界全部在服务端判定：
     * 未购买读者只能读取 floor(全书页数 * 10%)，翻页或直接访问越界页一律拒绝；
     * 已购读者（含下架前购买）可读全文。
     */
    public ApiResponse<PageContentResponse> getPage(String userId, String ebookId, int page) {
        Optional<Ebook> ebookOpt = ebookRepository.findById(ebookId);
        if (ebookOpt.isEmpty()) {
            return ApiResponse.error("电子书不存在");
        }
        Ebook ebook = ebookOpt.get();

        boolean purchased = userId != null && hasPurchased(userId, ebookId);
        if (!purchased && ebook.getStatus() != Ebook.Status.PUBLISHED) {
            return ApiResponse.error("该电子书已下架");
        }

        int pageCount = totalPages(ebook);
        if (pageCount == 0) {
            return ApiResponse.error("本书暂无可阅读内容");
        }
        if (page < 1 || page > pageCount) {
            return ApiResponse.error("页码超出范围");
        }

        int sampleEnd = sampleEndPage(ebook);
        if (!purchased && page > sampleEnd) {
            return ApiResponse.error("试读范围到此结束，购买后可阅读全文");
        }

        if (userId != null) {
            saveProgress(userId, ebookId, page, pageCount);
        }

        String content = ebook.getContentPages().get(page - 1);
        return ApiResponse.success(new PageContentResponse(page, pageCount, content, purchased, !purchased));
    }

    /**
     * 仅返回试读页内容，供详情页/分享场景使用，同样受边界约束。
     */
    public ApiResponse<List<String>> getSamplePages(String ebookId) {
        Optional<Ebook> ebookOpt = ebookRepository.findById(ebookId);
        if (ebookOpt.isEmpty()) {
            return ApiResponse.error("电子书不存在");
        }
        Ebook ebook = ebookOpt.get();
        if (ebook.getStatus() != Ebook.Status.PUBLISHED) {
            return ApiResponse.error("该电子书已下架");
        }
        int end = sampleEndPage(ebook);
        return ApiResponse.success(ebook.getContentPages().subList(0, end));
    }

    public boolean hasPurchased(String userId, String ebookId) {
        return orderRepository.existsByUserIdAndTypeAndItemIdAndStatus(
                userId, Order.OrderType.EBOOK_PURCHASE, ebookId, Order.Status.PAID);
    }

    private int totalPages(Ebook ebook) {
        int byContent = ebook.getContentPages() == null ? 0 : ebook.getContentPages().size();
        if (byContent > 0) {
            return byContent;
        }
        return ebook.getPageCount() == null ? 0 : ebook.getPageCount();
    }

    /**
     * 试读范围严格按全书实际内容页数的 10% 向下取整（不少于 1 页，前提是全书非空）。
     */
    private int sampleEndPage(Ebook ebook) {
        int total = totalPages(ebook);
        if (total <= 0) {
            return 0;
        }
        double percent = ebook.getSampleEndPercent() == null ? 0.1 : ebook.getSampleEndPercent();
        int end = (int) Math.floor(total * percent);
        return Math.max(end, 1);
    }

    private void saveProgress(String userId, String ebookId, int page, int pageCount) {
        ReadingProgress progress = readingProgressRepository
                .findByUserIdAndEbookId(userId, ebookId)
                .orElseGet(ReadingProgress::new);
        progress.setUserId(userId);
        progress.setEbookId(ebookId);
        progress.setCurrentPage(page);
        progress.setProgressPercent(pageCount == 0 ? 0.0 : (double) page / pageCount);
        progress.setUpdatedAt(LocalDateTime.now());
        readingProgressRepository.save(progress);
    }
}
