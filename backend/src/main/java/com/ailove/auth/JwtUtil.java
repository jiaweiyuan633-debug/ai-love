package com.ailove.auth;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * HS256 JWT 的签发与校验。生产环境必须用足够长的随机密钥（环境变量 JWT_SECRET）。
 */
public class JwtUtil {

    private final SecretKey key;
    private final long expireHours;

    public JwtUtil(String secret, long expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireHours = expireHours;
    }

    public String issue(long userId, String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireHours * 3600_000L))
                .signWith(key)
                .compact();
    }

    /** 校验并解析 token；非法或过期返回 null。 */
    public UserId parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            Object uid = claims.get("uid");
            if (uid instanceof Number number) {
                return new UserId(number.longValue(), claims.getSubject());
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public record UserId(long id, String username) {
    }
}
