package com.agentassist.service.auth;



import com.agentassist.dto.requestDTO.AuthRequest;
import com.agentassist.dto.requestDTO.RegisterRequest;
import com.agentassist.dto.responseDTO.AuthResponse;
import com.agentassist.model.UserEntity;
import com.agentassist.repository.UserRepository;
import com.agentassist.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository repo;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public String register(RegisterRequest req) {
        UserEntity user = new UserEntity();
        user.setName(req.getName());
        user.setEmail(req.getEmail());
        user.setPassword(encoder.encode(req.getPassword()));

        repo.save(user);

        return "User registered successfully";
    }


    public AuthResponse login(AuthRequest req) {
        var user = repo.findByEmail(req.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid login!"));

        if (!encoder.matches(req.getPassword(), user.getPassword())) {
            throw new RuntimeException("Wrong password!");
        }

        return new AuthResponse(jwt.generateToken(user.getEmail()));
    }
}
