package com.example.chatapplication.SecurityConfigration;

import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUntil jwtUntil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain ) throws jakarta.servlet.ServletException, java.io.IOException
                                    {
                                        String token=null;
                                        Cookie[] cookies=request.getCookies();
                                        if(cookies!=null)
                                        {
                                            for(Cookie cookie: cookies)
                                            {
                                                if("jwt".equals(cookie.getName()))
                                                {
                                                    token=cookie.getValue();
                                                    break;
                                                }
                                            }
                                        }
                                        if(token!=null)
                                        {
                                            if(jwtUntil.Token_is_vailid(token))
                                            {
                                                String email=jwtUntil.FechEmailfromToke(token);  
                                                if(SecurityContextHolder.getContext().getAuthentication()==null)
                                                    {
                                                        UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken( email,null,new ArrayList<>());
                                                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                                                        SecurityContextHolder.getContext().setAuthentication(auth);
                                                    }  
                                            }
                                            else
                                            {
                                                // Clear invalid cookie to prevent console flooding
                                                Cookie cookie = new Cookie("jwt", "");
                                                cookie.setPath("/");
                                                cookie.setMaxAge(0);
                                                response.addCookie(cookie);
                                            }
                                        }
                                        chain.doFilter(request, response);
                                        
                                    }

}
