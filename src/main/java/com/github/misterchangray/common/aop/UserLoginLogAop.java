package com.github.misterchangray.common.aop;

import com.github.misterchangray.common.NormalResponse;
import com.github.misterchangray.common.enums.DBEnum;
import com.github.misterchangray.common.utils.HttpRequestParserUtils;
import com.github.misterchangray.common.utils.JSONUtils;
import com.github.misterchangray.common.utils.MapBuilder;
import com.github.misterchangray.dao.entity.LoginLog;
import com.github.misterchangray.dao.entity.User;
import com.github.misterchangray.service.common.GlobalCacheService;
import com.github.misterchangray.service.log.LoginLogService;
import com.github.misterchangray.service.user.bo.UserSessionBo;
import com.github.misterchangray.service.user.vo.UserSessionVO;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.Map;


/**
 * 登录日志
 * <p>
 * 从业务上来讲：衹要是系統用戶;无论是否登录成功都应该记录日志;方便日后分析
 * 增加登录日志在 LoginService.signIn 方法中调用
 * 登出时间在用户 session 销毁时更新;即 UserSession
 *
 * @author Rui.Zhang/misterchangray@hotmail.com
 * @author Created on 2018/5/2.
 */
@Component
@Aspect
public class UserLoginLogAop {
    @Autowired
    LoginLogService loginLogService;
    @Autowired
    HttpServletRequest httpServletRequest;
    @Autowired
    UserSessionBo userSessionBo;
    @Autowired
    GlobalCacheService globalCacheService;

    //創建用戶session時;創建日志
    @Pointcut(value = "execution(com.github.misterchangray.common.NormalResponse com.github.misterchangray.service.user.LoginService.signInBy*(..))")
    private void createSession() {}

    //銷毀用戶session時;更新用戶登出時間
    @Pointcut(value = "execution(void com.github.misterchangray.service.user.bo.UserSessionBo.destroySession(..))")
    private void destroySession() {}


    @Around(value = "createSession()")
    public Object createSessionAround(ProceedingJoinPoint point) {
        Object res;
        try {
            res = point.proceed();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return throwable.getMessage();
        }

        NormalResponse normalResponse = (NormalResponse) res;
        if(null == normalResponse) return res;

//        if(false == normalResponse.isSuccess()) return res; //打开此行则只记录登录成功的用户

        User user = null;
        MapBuilder mapBuilder = null;
        if(null != normalResponse.getData()) {
            mapBuilder = (MapBuilder) normalResponse.getData();
            if(null != mapBuilder) {
                user = (User) mapBuilder.get("user");
            }
        }

        //登录日志数据
        LoginLog loginLog = new LoginLog();
        if(null != user) {
            loginLog.setUserId(user.getId());
        }
        loginLog.setSignInIp(HttpRequestParserUtils.getUserIpAddr(httpServletRequest));
        loginLog.setDeviceInfo(HttpRequestParserUtils.getUserAgent(httpServletRequest));
        loginLog.setSignInTime(new Date());
        loginLog.setSuccess(normalResponse.isSuccess() ? DBEnum.TRUE.getCode() : DBEnum.FALSE.getCode());
        if(false == normalResponse.isSuccess()) {
            loginLog.setDetailsOfFail(normalResponse.getErrorMsg());
        }
        loginLog.setSignInParam(JSONUtils.obj2json(point.getArgs()));
        int id =loginLogService.addLog(loginLog);

        //如果登录成功则更日志ID到缓存中
        if(normalResponse.isSuccess()) {
            Map<String, UserSessionVO> userSessionVOMap = (Map<String, UserSessionVO>) globalCacheService.get("onLineUsers");
            UserSessionVO userSessionVO = userSessionVOMap.get(String.valueOf(user.getId()));
            if(0 != id && null != userSessionVO && null != loginLog.getId()) {
                userSessionVO.setLoginLogId(loginLog.getId());
            }
        }

        return res;
    }


    @Around(value = "destroySession()")
    public Object destroySessionAround(ProceedingJoinPoint point) {
        Object res;

        UserSessionVO userSessionVO = userSessionBo.getSession((String) point.getArgs()[0]);
        loginLogService.addSignOutTime(userSessionVO.getLoginLogId());

        try {
            res = point.proceed();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return throwable.getMessage();
        }
        return res;
    }
}
