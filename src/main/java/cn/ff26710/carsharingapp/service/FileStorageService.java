package cn.ff26710.carsharingapp.service;

import cn.ff26710.carsharingapp.vo.FileVO;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    FileVO store(MultipartFile file, String biz);
}
