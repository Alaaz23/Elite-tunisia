package tn.elitetunisia.auth.dto;

import java.util.Objects;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class RegisterRequest {
        @NotBlank
        @Email
        @Size(max = 254)
        private String email;

        @NotBlank
        @Size(max = 80)
        private String firstName;

        @NotBlank
        @Size(max = 80)
        private String lastName;

        @NotBlank
        @Pattern(regexp = "^\\+?[0-9][0-9 ()-]{7,38}$", message = "Saisissez un numéro de téléphone valide.")
        private String phone;

        @NotBlank
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{12,72}$",
                        message = "Le mot de passe doit contenir 12 caractères, une minuscule, une majuscule, un chiffre et un symbole.")
        private String password;

        @NotBlank
        private String confirmPassword;

        public RegisterRequest() { }

        public String getFirstName() { return firstName; }
        public String getLastName() { return lastName; }
        public String getEmail() { return email; }
        public String getPhone() { return phone; }
        public String getPassword() { return password; }
        public String getConfirmPassword() { return confirmPassword; }

        public void setFirstName(String firstName) { this.firstName = firstName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public void setEmail(String email) { this.email = email; }
        public void setPhone(String phone) { this.phone = phone; }
        public void setPassword(String password) { this.password = password; }
        public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }

        @AssertTrue(message = "La confirmation du mot de passe ne correspond pas.")
        public boolean isPasswordConfirmed() {
                return password != null && Objects.equals(password, confirmPassword);
        }

}