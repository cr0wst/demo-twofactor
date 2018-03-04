# Beefing Up Your Spring Security with Two-Factor Authentication

Two-factor authentication adds an extra layer of security to your web application by asking users to provide a second form of identification.  Common second factors include:

* Authenticator codes
* Biometrics
* Email or Text Message codes 

Let's explore how you can add two-factor authentication to an existing web application by utilizing Nexmo.

# Before you begin
In order to follow along with the tutorial you will need the following:

* A general understanding of Java and Enterprise Java technologies
* The [Java SDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html) installed on your computer
* A [Nexmo Developer Account](https://developer.nexmo.com/) along with an api key and secret
* A clone of the [`getting-started`](https://github.com/cr0wst/demo-twofactor/tree/getting-started) branch on GitHub

# Get the Code
First, clone the `getting-started` branch.
```
$ git clone git@github.com:cr0wst/demo-twofactor.git -b getting-started
$ cd demo-twofactor
```

# Let's See What We're Working With
The example application is built using [Spring Boot](https://projects.spring.io/spring-boot/).  If you have [Gradle](https://gradle.org/) installed on your system, you should be able to execute the `bootRun` task to start the application.

If not, no worries, the repository contains a Gradle wrapper which will still allow you to execute tasks.

```
$ ./gradlew bootRun
``` 

This will download any dependencies, compile the application, and start the embedded server.  

Once the server has been started, you should be able to navigate to http://localhost:8080 to see the sample application.

There are three pages:

* The [home page](http://localhost:8080/login) - accessible by everybody.
* The [login page](http://localhost:8080/login) - allows users to enter a username and password (default is `demo`/`demo`).
* The [secret page](http://localhost:8080/seret) - accessible only by users with the `Role.USERS` role.

# Adding Two-Factor Authentication
When a user logs in our only acceptance criteria is that they have provided a username and a password.  What if this information was stolen?  What is something that we could use physically located near the user?

There is something that I guarantee almost 90% of you have within arm's reach.  A mobile phone. 

Here's how it's going to work:

1. The user will login to our application as they normally do.
2. They will be prompted to enter a four-digit verification code.
3. Simultaneously a four-digit verification code will be sent to the phone number on their account.  If they don't have a phone number on their account, we will allow them to bypass the two-factor authentication.
4. The code that they enter will be checked to make sure it is the same one that we sent them.

We are going to utilize the Nexmo [Verify API](https://developer.nexmo.com/verify/overview) to generate the code, and to check and see if the code they entered is valid.

## Creating a New Role
The first step will be to create a new role.  This role will be used to hold the authenticated user in a purgatory state until we have verified their identity. 

Add the `PRE_VERIFICATION_USER` role to the `Role` enum.

```java
// src/main/net/smcrow/demo/twofactor/user/Role.java
public enum Role implements GrantedAuthority {
    USER, PRE_VERIFICATION_USER;
    // ...
}
```

In order for it to be applied as the default role, we need to update the `getAuthorities()` method of the `StandardUserDetails` class.  Add the following:
		
```java
// src/main/net/smcrow/demo/twofactor/user/StandardUserDetails.java
@Override
public Collection<? extends GrantedAuthority> getAuthorities() {
    Set<GrantedAuthority> authorities = new HashSet<>();
    authorities.add(Role.PRE_VERIFICATION_USER);
    return authorities;
}
```

## Handling Verification Information
Nexmo will provide us with a *request id* that we will need to use when confirming the code provided by the user. There are a variety of ways we can store this information. In this tutorial, we will be persisting it into a database.

### Storing `Verification` Information
First create a `Verification` class in the `verify` package.

```java
// src/main/net/smcrow/demo/twofactor/verify/Verification.java
@Entity
public class Verification {
    @Id
    @Column(unique = true, nullable = false)
    private String phone;

    @Column(nullable = false)
    private String requestId;

    @Column(nullable = false)
    private Date expirationDate;

    @PersistenceConstructor
    public Verification() {
        // Empty constructor for JPA
    }

    public Verification(String phone, String requestId, Date expirationDate) {
        this.phone = phone;
        this.requestId = requestId;
        this.expirationDate = expirationDate;
    }

	// ... Getters and Setters
}
```

Notice, we are also storing the `expirationDate`.  By default, the Verify API requests are only valid for five minutes.  They will get deleted from the table when either:

* The user has successfully verified their identity.
* They are expired.

We will take advantage of Spring Scheduler to clean them up periodically.

### Working with the Verification Information
Create the `VerificationRepository` interface in the `verify` package.

```java
// src/mainnet/smcrow/demo/twofactor/verify/VerificationRepository.java
@Repository
public interface VerificationRepository extends JpaRepository<Verification, String> {
    Optional<Verification> findByPhone(String phone);

    void deleteByExpirationDateBefore(Date date);
}
```

### Deleting Expired Requests
In the `twofactor` package, create the following configuration class.

```java
// src/main/net/smcrow/demo/twofactor/ScheduleConfiguration.java
@Configuration
@EnableScheduling
public class ScheduleConfiguration {
    @Autowired
    private VerificationRepository verificationRepository;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void purgeExpiredVerifications() {
        verificationRepository.deleteByExpirationDateBefore(new Date());
    }
}
```

This will set up a scheduled command to be executed every second that will query for any expired `Verification` entities and delete them.

## Setting Up the Nexmo Client
We will be using the [nexmo-java](https://github.com/Nexmo/nexmo-java) client for interacting with Nexmo.

### Declare the Dependency
First, add the following dependency to your `build.gradle` file.

```groovy
dependencies {
	// .. other dependencies
	compile('com.nexmo:client:3.3.0')
}
```

### Provide Information
Now, add the following information to your `application.properties` file.

```
# Add your nexmo credentials
nexmo.api.key=your-api-key
nexmo.api.secret=your-api-secret
```

### Define the Beans
Next, we are going to define the `NexmoClient` and `VerifyClient` as beans.  This will allow Spring to inject them as dependencies into our `NexmoVerificationService`.  Add the following definitions to the `TwoFactorApplication` class.

```java
// src/main/net/smcrow/demo/twofactor/TwofactorApplication.java
@Bean
public NexmoClient nexmoClient(Environment environment) {
	AuthMethod auth = new TokenAuthMethod(
			environment.getProperty("nexmo.api.key"),
			environment.getProperty("nexmo.api.secret")
	);
	return new NexmoClient(auth);
}

@Bean
public VerifyClient nexmoVerifyClient(NexmoClient nexmoClient) {
	return nexmoClient.getVerifyClient();
}
```

### Create the `NexmoVerificationService`
We are going to create a service that will allow us to instruct the client to make requests.

Add the `NexmoVerificationService` to the `verify` package.

```java
// src/main/net/smcrow/demo/twofactor/verify/NexmoVerificationService.java
@Service
public class NexmoVerificationService {
    private static final String APPLICATION_BRAND = "2FA Demo";
    private static final int EXPIRATION_INTERVALS = Calendar.MINUTE;
    private static final int EXPIRATION_INCREMENT = 5;
    @Autowired
    private VerificationRepository verificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerifyClient verifyClient;

    public Verification requestVerification(String phone) throws VerificationRequestFailedException {
        Optional<Verification> matches = verificationRepository.findByPhone(phone);
        if (matches.isPresent()) {
            return matches.get();
        }

        return generateAndSaveNewVerification(phone);
    }

    public boolean verify(String phone, String code) throws VerificationRequestFailedException {
        try {
            Verification verification = retrieveVerification(phone);
            if (verifyClient.check(verification.getRequestId(), code).getStatus() == 0) {
                verificationRepository.delete(phone);
                return true;
            }

            return false;
        } catch (VerificationNotFoundException e) {
            requestVerification(phone);
            return false;
        } catch (IOException | NexmoClientException e) {
            throw new VerificationRequestFailedException(e);
        }
    }

    private Verification retrieveVerification(String phone) throws VerificationNotFoundException {
        Optional<Verification> matches = verificationRepository.findByPhone(phone);
        if (matches.isPresent()) {
            return matches.get();
        }

        throw new VerificationNotFoundException();
    }

    private Verification generateAndSaveNewVerification(String phone) throws VerificationRequestFailedException {
        try {
            VerifyResult result = verifyClient.verify(phone, APPLICATION_BRAND);
            if (StringUtils.isBlank(result.getErrorText())) {
                String requestId = result.getRequestId();
                Calendar now = Calendar.getInstance();
                now.add(EXPIRATION_INTERVALS, EXPIRATION_INCREMENT);

                Verification verification = new Verification(phone, requestId, now.getTime());
                return verificationRepository.save(verification);
            }
        } catch (IOException | NexmoClientException e) {
            throw new VerificationRequestFailedException(e);
        }

        throw new VerificationRequestFailedException();
    }
}
```

There are two main methods in this class:

* `requestVerficiation` which is used to, well, request verification.
* `verify` which is used to verify the provided code provided by the user.

#### The `requestVerification` Method
The method first checks to see if we already have a pending verification request for the user's phone number.  This allows us to serve the same request id to the user if they attempt to login to the application again.

If there isn't any prior verification, then a new verification code is requested and saved to the database.  If, for some reason, we are unable to assign them a new code a `VerificationRequestFailedException` is thrown.

Add this exception to the `verify` package.

```java
// src/main/net/smcrow/demo/twofactor/verify/VerificationRequestFailedException.java
public class VerificationRequestFailedException extends Throwable {
    public VerificationRequestFailedException() {
        this("Failed to verify request.");
    }

    public VerificationRequestFailedException(String message) {
        super(message);
    }

    public VerificationRequestFailedException(Throwable cause) {
        super(cause);
    }
}
```

#### The `verify` Method
The `verify` method sends the request id and code to Nexmo for verification.  Nexmo returns a zero status if the verification was successful.  On successful verification, the `Verification` entity is removed from the database, and `true` is returned.

If we were unable to find the `Verification` entity, maybe it expired, we request a new one and return false.  If there are any issues verifying, we throw a `VerificationRequestFailedException`.

The `retrieveVerification` method will throw a `VerificationNotFoundException` if the `Verification` wasn't found.

Add this exception to the `verify` package.

```java
// src/main/net/smcrow/demo/twofactor/verify/VerificationNotFoundException.java

public class VerificationNotFoundException extends Throwable {
    public VerificationNotFoundException() {
        this("Failed to find verification.");
    }

    public VerificationNotFoundException(String message) {
        super(message);
    }
}
```

## Using the `NexmoVerificationService`
We're going to use the service for both sending a code and verifying the code.  Sending a code is done after a successful login.

### Triggering the Request for Verification
Let's implement a custom `AuthenticationSuccessHandler` which will be called after the user has successfully authenticated.

Add the `TwoFactorAuthenticationSuccessHandler` to the `verify` package.

```java
// src/main/net/smcrow/demo/twofactor/verify/TwoFactorAuthenticationSuccessHandler.java
@Component
public class TwoFactorAuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    private static final String VERIFICATION_URL = "/verify";
    private static final String INDEX_URL = "/";

    @Autowired
    private NexmoVerificationService verificationService;

    @Autowired
    private UserRepository userRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        String phone = ((StandardUserDetails) authentication.getPrincipal()).getUser().getPhone();
        if (phone == null || !requestAndRegisterVerification(phone)) {
            bypassVerification(request, response, authentication);
            return;
        }

        new DefaultRedirectStrategy().sendRedirect(request, response, VERIFICATION_URL);
    }

    private boolean requestAndRegisterVerification(String phone) {
        try {
            return verificationService.requestVerification(phone) != null;
        } catch (VerificationRequestFailedException e) {
            return false;
        }
    }

    private void bypassVerification(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        verificationService.updateAuthentication(authentication);
        new DefaultRedirectStrategy().sendRedirect(request, response, INDEX_URL);
    }
}
```

When a user has successfully authenticated we check to see if they have a phone number.  

If they have a phone number, we request that a code be sent to their device. If they don't, or we are unable to send a code, we allow them to bypass verification.  

The `bypassVerification` method relies on the `updateAuthentication` method of the `NexmoVerificationService`.  Add this to the `NexmoVerificationService`:

```java
// src/main/net/smcrow/demo/twofactor/verify/NexmoVerificationService.java
public void updateAuthentication(Authentication authentication) {
    Role role = retrieveRoleFromDatabase(authentication.getName());
    List<GrantedAuthority> authorities = new ArrayList<>();
    authorities.add(role);

    Authentication newAuthentication = new UsernamePasswordAuthenticationToken(
            authentication.getPrincipal(),
            authentication.getCredentials(),
            authorities
    );

    SecurityContextHolder.getContext().setAuthentication(newAuthentication);
}

private Role retrieveRoleFromDatabase(String username) {
    Optional<User> match = userRepository.findByUsername(username);
    if (match.isPresent()) {
        return match.get().getRole();
    }

    throw new UsernameNotFoundException("Username not found.");
}
```

This method is used to assign the `role` defined in the database to the current user and removes the `PRE_VERIFICATION_USER` role.

### Prompting The User for a Code
Once the user has been sent a code they are forwarded to the [verification](http://localhost:8080/verify) page.  Let's work on creating that page next.

The example application is setup using [Thymeleaf](https://www.thymeleaf.org/).  Create a new HTML file called `verify.html` in the `resources/templates` directory.

```html
<!DOCTYPE html>
<html lang="en"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorator="default">
<head>
    <meta charset="UTF-8" />
    <title>Two Factor Authorization Demo</title>
</head>
<body>
<div layout:fragment="content" class="container">
    <div class="col-lg-12 alert alert-danger text-center" th:if="${param.error}">There was an error with your login.</div>
    <div class="col-lg-4 offset-lg-4 text-left">
        <form th:action="@{/verify}" method="post">
            <h1>Verify</h1>
            <p>A text message has been sent to your mobile device.  Please enter the code below:</p>
            <div class="form-group">
                <label for="code">Verification Code</label>
                <input type="text" class="form-control" id="code" name="code" placeholder="4-Digit Code" />
            </div>
            <button type="submit" class="btn btn-primary">Verify</button>
        </form>
    </div>
</div>
</body>
</html>
```

We also need a controller to serve the page to the user.  Create the `VerificationController` in the `verify` package.

```java
// src/main/net/smcrow/demo/twofactor/verify/VerificationController.java
@Controller
public class VerificationController {
    @Autowired
    private NexmoVerificationService verificationService;

    @PreAuthorize("hasRole('PRE_VERIFICATION_USER')")
    @GetMapping("/verify")
    public String index() {
        return "verify";
    }

    @PreAuthorize("hasRole('PRE_VERIFICATION_USER')")
    @PostMapping("/verify")
    public String verify(@RequestParam("code") String code, Authentication authentication) {
        User user = ((StandardUserDetails) authentication.getPrincipal()).getUser();
        try {
            if (verificationService.verify(user.getPhone(), code)) {
                verificationService.updateAuthentication(authentication);
                return "redirect:/";
            }

            return "redirect:verify?error";
        } catch (VerificationRequestFailedException e) {
            // Having issues generating keys let them through.
            verificationService.updateAuthentication(authentication);
            return "redirect:/";
        }
    }
}
```

This controller serves the verification page via the `index` method, and handles the form submission via the `verify` method.

This page is only accessible to users with the `PRE_VERIFICATION_USER` role.  On successful verification, the `updateAuthentication` method is, once again, used to replace this role with their persisted one.

### Finishing Up the Verification Chain
The final step is to update the `AppSecurityConfiguration` to use our `TwoFactorAuthenticationSuccessHandler`.

Modify the `AppSecurityConfiguration` to wire in our handler and use it via the `successHandler` method.

```java
// src/main/net/smcrow/demo/twofactor/AppSecurityConfiguration.java
@Autowired
private TwoFactorAuthenticationSuccessHandler twoFactorAuthenticationSuccessHandler;

@Override
protected void configure(HttpSecurity httpSecurity) throws Exception {
    // Webjar resources
    httpSecurity.authorizeRequests().antMatchers("/webjars/**").permitAll()
    .and().formLogin().loginPage("/login").permitAll()
            .successHandler(twoFactorAuthenticationSuccessHandler)
    .and().logout().permitAll();
}
```

## Try it Out!
You will need to add your phone number to the `data.sql` file. 

We aren't going to be doing any validation on the phone number, and it needs to be in [E.164](https://en.wikipedia.org/wiki/E.164) format.

```sql
INSERT INTO user (username, password, role, phone) VALUES
    ('phone', 'phone', 'USER', 15555555555);
```

You should now be up and running.  Boot up the application, and try to login.  Assuming that your api key, api secret, and seeded phone number are correct; you should receive a text message with a four-digit code.

## What Did We Do?
We did a ***lot*** of things.  

In short, we implemented two-factor authentication to better secure our application.  We did this by:

* Creating a custom `AuthenticationSuccessHandler` to forward the user to a verification page after serving them a code.
* Using the `nexmo-java` library, by wrapping it in a `NexmoVerificationService` to send verification codes to our users.
* Taking advantage of the Spring Scheduler to delete expired verification codes.
* Building a page for the user to enter their verification code.

Check out the [final code from this tutorial](https://github.com/cr0wst/demo-twofactor/tree/final-with-auth-success-handler) on GitHub.

Also, take a look at [this alternative method](https://github.com/cr0wst/demo-twofactor/tree/final-with-sessions) using sessions to store request information.

## Looking Ahead
There are various ways that two-factor authentication can be implemented.  If you're curious about any of the frameworks and technologies used in the sample code, here's a rundown:

* [Spring Boot](http://projects.spring.io/spring-boot/)
* [Spring Security](https://projects.spring.io/spring-security/)
* [Gradle](https://gradle.org/)

Don't forget that *you* can be a Nexmo contributor to the [nexmo-java](https://github.com/Nexmo/nexmo-java) client.
