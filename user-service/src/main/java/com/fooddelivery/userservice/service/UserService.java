package com.fooddelivery.userservice.service;

import com.fooddelivery.userservice.dto.*;
import com.fooddelivery.userservice.exception.ConflictException;
import com.fooddelivery.userservice.exception.NotFoundException;
import com.fooddelivery.userservice.exception.UnauthorizedException;
import com.fooddelivery.userservice.model.Address;
import com.fooddelivery.userservice.model.Role;
import com.fooddelivery.userservice.model.User;
import com.fooddelivery.userservice.repository.UserRepository;
import com.fooddelivery.userservice.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserResponse register(RegisterRequest req) {
        if (req.role() == Role.ADMIN) {
            throw new IllegalArgumentException("Não é possível se cadastrar como ADMIN");
        }
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("E-mail já cadastrado");
        }
        User user = new User();
        user.setName(req.name());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setPhone(req.phone());
        user.setRole(req.role());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.email().trim().toLowerCase())
                .orElseThrow(() -> new UnauthorizedException("E-mail ou senha inválidos"));
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("E-mail ou senha inválidos");
        }
        return new LoginResponse(jwtService.generate(user), "Bearer", UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return UserResponse.from(findUser(id));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest req) {
        User user = findUser(id);
        user.setName(req.name());
        user.setPhone(req.phone());
        return UserResponse.from(user);
    }

    @Transactional
    public AddressResponse addAddress(Long userId, AddressRequest req) {
        User user = findUser(userId);

        // só um endereço principal por usuário
        if (req.main()) {
            user.getAddresses().forEach(a -> a.setMain(false));
        }

        Address address = new Address();
        address.setStreet(req.street());
        address.setNumber(req.number());
        address.setComplement(req.complement());
        address.setNeighborhood(req.neighborhood());
        address.setCity(req.city());
        address.setState(req.state().toUpperCase());
        address.setZipCode(req.zipCode());
        address.setMain(req.main() || user.getAddresses().isEmpty());
        user.addAddress(address);

        userRepository.saveAndFlush(user);
        return AddressResponse.from(address);
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> listAddresses(Long userId) {
        return findUser(userId).getAddresses().stream().map(AddressResponse::from).toList();
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
    }
}
