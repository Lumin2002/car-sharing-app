package cn.ff26710.carsharingapp.aspect;

import cn.ff26710.carsharingapp.annotation.OperLogAnnotation;
import cn.ff26710.carsharingapp.entity.OperLog;
import cn.ff26710.carsharingapp.entity.User;
import cn.ff26710.carsharingapp.entity.enums.LogResults;
import cn.ff26710.carsharingapp.service.AsyncService;
import cn.ff26710.carsharingapp.utils.IpUtil;
import cn.ff26710.carsharingapp.utils.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.concurrent.RejectedExecutionException;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class OperLogAspect {
    private final AsyncService asyncService;

    @Around("@annotation(operLogAnnotation)")
    public Object around(ProceedingJoinPoint joinPoint, OperLogAnnotation operLogAnnotation) throws Throwable {
        OperLog operLog = new OperLog();
        operLog.setOperTime(LocalDateTime.now());

        operLog.setOperType(operLogAnnotation.operType());
        operLog.setOperDesc(operLogAnnotation.operDesc());

        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes != null){
            HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
            String ip = IpUtil.getClientIp(request);
            operLog.setIp(ip);
        }
        User loginUser = SecurityUtil.getLoginUser();
        if (loginUser != null) {
            operLog.setUserId(loginUser.getUserId());
            }
        Object result;
        try {
            result = joinPoint.proceed();
            operLog.setResults(LogResults.SUCCESS);
            operLog.setMsg("");
        } catch (Throwable e) {
            operLog.setResults(LogResults.FAIL);
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.length() > 512) {
                errorMsg = errorMsg.substring(0, 512);
            }
            operLog.setMsg(errorMsg);
            throw e;
        } finally {
            try {
                asyncService.saveOperLog(operLog);
            } catch (RejectedExecutionException e) {
                log.error("切面异步插入操作日志时发生异常，线程队列已满，已进入同步写入操作日志");
                asyncService.saveOperLogSync(operLog);
            } catch (Exception e) {
                log.error("切面调用日志保存方法发生未知异常", e);
            }
        }
        return result;
    }
}
