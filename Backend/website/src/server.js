const fs = require("fs");
const https = require("https");
const express = require('express');
const session = require('express-session');
const passport = require('passport');
const OpenIDConnectStrategy = require('passport-openidconnect');

const app = express();

// Session Setup
app.use(session({
    secret: 'super-secret',
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
    callbackURL: process.env.OIDC_REDIRECT_URI
}, (issuer, sub, profile, accessToken, refreshToken, done) => {
    return done(null, profile);
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

// Routes
app.get('/', (req, res) => {
    res.render('home');
});

app.get('/login', passport.authenticate('openidconnect'));

app.get('/auth/callback', passport.authenticate('openidconnect', {
    successRedirect: '/supportUni',
    failureRedirect: '/'
}));

// supportUni route
app.get('/supportUni', (req, res) => {
    res.render('supportUni');
});

// supportMacchinette route
app.get('/supportMacchinette', (req, res) => {
    res.render('supportMacchinette');
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