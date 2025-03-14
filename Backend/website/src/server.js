const fs = require("fs");
const https = require("https");
const express = require('express');
const session = require('express-session');
const passport = require('passport');
const OpenIDConnectStrategy = require('passport-openidconnect');
const axios = require('axios');
const jwt = require('jsonwebtoken');

const httpsAgent = new https.Agent({
    ca: fs.readFileSync('/certs/ca.crt'),
});

const app = express();

// Session Setup
app.use(session({
    secret: 'This is my super secret key that nobody should know and nobody should be able to guess i\'m to lazy to make it a .env variable so i\'m just gonna leave it here in plain text and hope nobody finds it and uses it to steal my session data and impersonate me on my own website but i\'m sure it\'ll be fine right? right? what could possibly go wrong? right?',
    resave: false,
    saveUninitialized: true
}));

// Passport Setup
passport.use(new OpenIDConnectStrategy({
    issuer: process.env.OIDC_ISSUER,
    clientID: process.env.OIDC_CLIENT_ID,
    clientSecret: process.env.OIDC_CLIENT_SECRET,
    authorizationURL: `${process.env.OIDC_ISSUER}/protocol/openid-connect/auth`,
    tokenURL: `${process.env.OIDC_ISSUER}/protocol/openid-connect/token`,
    userInfoURL: `${process.env.OIDC_ISSUER}/protocol/openid-connect/userinfo`,
    callbackURL: `${process.env.OIDC_REDIRECT_URI}/auth/callback`,
    agent: httpsAgent,
}, (issuer, profile, context, idToken, accessToken, refreshToken, done) => {
    console.log("Authenticated user:", profile);
    const expiresAt = Date.now() + (context.expires_in * 1000); // Store expiration time
    return done(null, { profile, idToken, accessToken, refreshToken, expiresAt });
}));

passport.serializeUser((user, done) => done(null, user));
passport.deserializeUser((obj, done) => done(null, obj));

app.use(passport.initialize());
app.use(passport.session());

app.set('view engine', 'ejs');
app.set('views', __dirname + '/views');

// Path file statici
app.use(express.static(__dirname + '/public'));

// Middleware
app.use(express.json());
app.use(express.urlencoded({ extended: false }));

async function refreshAccessToken(user) {
    if (!user.refreshToken) {
        throw new Error("No refresh token available");
    }

    const tokenURL = `${process.env.OIDC_ISSUER}/protocol/openid-connect/token`;

    try {
        const response = await axios.post(tokenURL, new URLSearchParams({
            grant_type: 'refresh_token',
            client_id: process.env.OIDC_CLIENT_ID,
            client_secret: process.env.OIDC_CLIENT_SECRET,
            refresh_token: user.refreshToken
        }), {
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            httpsAgent: httpsAgent
        });

        // Update user session
        user.accessToken = response.data.access_token;
        user.expiresAt = Date.now() + (response.data.expires_in * 1000);
        user.refreshToken = response.data.refresh_token || user.refreshToken;
    } catch (error) {
        console.error("Failed to refresh access token:", error.response?.data || error.message);
        throw new Error("Failed to refresh access token");
    }
}

//middleware per assicurare che l'utente è autenticato e il token è aggiornato
function makeAuthetication(redirect = false){
    return async function ensureAuthenticated(req, res, next) {
        if (!req.isAuthenticated()) {
            if(!redirect)
                return res.status(401).json({ message: 'Not authenticated, please log in' });
            else
                return res.redirect("/login");
        }

        try {
            // Check if access token is expired
            if (Date.now() >= req.user.expiresAt) {
                console.log("Access token expired, refreshing...");
                await refreshAccessToken(req.user);
            }
            next();
        } catch (error) {
            return res.status(401).json({ message: 'Failed to refresh access token, please log in again' });
        }
    }
}

// Auth routes
app.get('/login', passport.authenticate('openidconnect'));

app.post('/logout', (req, res) => {
    const keycloakLogoutUrl = `${process.env.OIDC_ISSUER}/protocol/openid-connect/logout` +
        `?post_logout_redirect_uri=${encodeURIComponent(process.env.OIDC_REDIRECT_URI)}` +
        `&id_token_hint=${encodeURIComponent(req.user.idToken)}`;
        
    req.logout(function(err) {
        if (err) { return next(err); }
        req.session.destroy(() => {
            res.redirect(keycloakLogoutUrl);
        });
    });
});

app.get('/auth/callback', passport.authenticate('openidconnect', {
    successRedirect: '/supportUni',
    failureRedirect: '/'
}));

// Routes
app.get('/', (req, res) => {
    res.render('home', { logout: req.isAuthenticated() });
});

app.get('/supportUni', makeAuthetication(true), (req, res) => {
    res.render('supportUni', { roles: jwt.decode(req.user.accessToken).realm_access.roles });
});

app.get('/supportMacchinette', makeAuthetication(true), (req, res) => {
    res.render('supportMacchinette', { roles: jwt.decode(req.user.accessToken).realm_access.roles });
});

// api proxy routes use axios to forward the request to the API using caCert to validate 
// the server certificate and req.user.accessToken to authorize the request
app.all('/api/*', makeAuthetication(false), async (req, res) => {
    try {
        const url = process.env.API_URL + req.url.replace('/api', '');
        const response = await axios({
            method: req.method,
            url,
            data: req.body,
            headers: {
                'Authorization': `Bearer ${req.user.accessToken}`,
            },
            httpsAgent: httpsAgent,
        });

        res.json(response.data);
    } catch (error) {
        res.status(error.response?.status || 500).json(error.response?.data || { message: 'Internal server error' });
    }
});

// Start server
const PORT = process.env.PORT || 3000;

// Read SSL certificate and key files
const options = {
    key: fs.readFileSync("/certs/https.key"),
    cert: fs.readFileSync("/certs/https.crt"),
};

// Create HTTPS server
const server = https.createServer(options, app);

server.listen(PORT, () => {
    console.log(`Server avviato su https://localhost:${PORT}`);
});