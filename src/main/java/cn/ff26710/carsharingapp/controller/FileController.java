package cn.ff26710.carsharingapp.controller;

import cn.ff26710.carsharingapp.service.FileStorageService;
import cn.ff26710.carsharingapp.vo.FileVO;
import cn.ff26710.carsharingapp.vo.ResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResultVO<FileVO> upload(@RequestPart("file") MultipartFile file,
                                   @RequestParam(defaultValue = "other") String biz) {
        return ResultVO.success(fileStorageService.store(file, biz));
    }
}
