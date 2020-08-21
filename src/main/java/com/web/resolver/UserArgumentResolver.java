package com.web.resolver;

import com.web.annotation.SocialUser;
import com.web.domain.User;
import com.web.domain.enums.SocialType;
import com.web.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class UserArgumentResolver implements HandlerMethodArgumentResolver {

    private UserRepository userRepository;

    @Autowired
    public void setUserRepository(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterAnnotation(SocialUser.class) != null &&
                parameter.getParameterType().equals(User.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception{
        HttpSession session = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest().getSession();
        User user = (User)session.getAttribute("user");
        return getUser(user, session);
    }

    private User getUser(User user, HttpSession session){
        if(user == null){
            try{
                OAuth2AuthenticationToken authentication = (OAuth2AuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
                Map<String, Object> map = authentication.getPrincipal().getAttributes();
                User convertUser = convertUser(authentication.getAuthorizedClientRegistrationId(), map);

                assert convertUser != null;
                user = userRepository.findByEmail(convertUser.getEmail());

                if(user == null){
                    user = userRepository.save(convertUser);
                }
                setRoleIfNotSame(user, authentication, map);
                session.setAttribute("user", user);
            }catch(ClassCastException cce){
                return user;
            }
        }
        return user;
    }

    private User convertUser(String authority, Map<String, Object> map){
        if(SocialType.FACEBOOK.getValue().equals(authority)) return getModernUser(SocialType.FACEBOOK, map);
        else if(SocialType.GOOGLE.getValue().equals(authority)) return getModernUser(SocialType.GOOGLE, map);
        else if(SocialType.KAKAO.getValue().equals(authority)) return getKakaoUser(map);
        else if(SocialType.NAVER.getValue().equals(authority)) return getNaverUser(map);
        return null;
    }

    private User getModernUser(SocialType socialType, Map<String, Object> map){
        return User.builder()
                .name(String.valueOf(map.get("name")))
                .email(String.valueOf(map.get("email")))
                .principal(String.valueOf(map.get("id")))
                .socialType(socialType)
                .createdDate(LocalDateTime.now())
                .build();
    }

    private User getKakaoUser(Map<String, Object> map){
        HashMap<String, String> propertyMap = (HashMap<String, String>)map.get("properties");
        User kakaoUser = User.builder()
                .name(String.valueOf(propertyMap.get("nickname")))
                .socialType(SocialType.KAKAO)
                .createdDate(LocalDateTime.now())
                .build();
        propertyMap = (HashMap<String, String>)map.get("kakao_account");
        kakaoUser.setEmail(String.valueOf(propertyMap.get("email")));
        return kakaoUser;
    }

    private User getNaverUser(Map<String, Object> map){
        HashMap<String, String> propertyMap = (HashMap<String, String>) map.get("response");
        return User.builder()
                .name(String.valueOf(propertyMap.get("nickname")))
                .email(String.valueOf(propertyMap.get("email")))
                .socialType(SocialType.NAVER)
                .createdDate(LocalDateTime.now())
                .build();
    }

    private void setRoleIfNotSame(User user, OAuth2AuthenticationToken auth2Authentication, Map<String, Object> map){
        if (!auth2Authentication.getAuthorities().contains(new SimpleGrantedAuthority(user.getSocialType().getRoleType()))) {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(map, "N/A",
                            AuthorityUtils.createAuthorityList(user.getSocialType().getRoleType()))
            );
        }
    }
}
