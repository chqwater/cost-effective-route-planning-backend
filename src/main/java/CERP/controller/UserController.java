package CERP.controller;

import CERP.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody Map<String, String> loginData) {
        String username = loginData.get("username");
        String passwordHash = loginData.get("passwordHash");

        if (userService.login(username, passwordHash)) {
            return ResponseEntity.ok("login successful");
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("username or password are wrong");
        }
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody Map<String, String> registerData) {
        String username = registerData.get("username");
        String email = registerData.get("email");
        String passwordHash = registerData.get("passwordHash");

        userService.register(username, email, passwordHash);
        return ResponseEntity.ok("successfully registered!");
    }


    @PostMapping("username")
    public ResponseEntity<String> checkUsername(@RequestBody Map<String, String> data){
        String username = data.get("username");

        if(userService.isUsernameExist(username)){
            return ResponseEntity.status(HttpStatus.CONFLICT).body("username exist");
        }
        return ResponseEntity.ok("acceptable username");
    }

}
