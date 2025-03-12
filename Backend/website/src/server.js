const fs = require("fs");
const https = require("https");
const express = require('express');
const session = require('express-session');
const passport = require('passport');
const OpenIDConnectStrategy = require('passport-openidconnect');
const axios = require('axios');

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
}, (issuer, profile, context, idToken, accessToken, refreshToken, done) => {
    return done(null, {profile, accessToken});
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


// Auth routes
app.get('/login', passport.authenticate('openidconnect'));

app.get('/logout', (req, res) => {
    req.logout(() => {
        res.redirect('/');
    });
});

app.get('/auth/callback', passport.authenticate('openidconnect', {
    successRedirect: '/supportUni',
    failureRedirect: '/'
}));

// Routes
app.get('/', (req, res) => {
    res.render('home');
});

app.get('/home', (req, res) => {
    res.render('home');
});

app.get('/supportUni', (req, res) => {
    res.render('supportUni');
});

app.get('/supportMacchinette', (req, res) => {
    res.render('supportMacchinette');
});

caCert = fs.readFileSync('/certs/ca.crt');

// api proxy routes use axios to forward the request to the API using caCert to validate the server certificate and req.user.accessToken to authorize the request
app.all('/api/*',(req, res) => {
    // check if user is authenticated using passport
    if (!req.isAuthenticated()) {
        res.status(401).json({message: 'Not authenticated user need to login'});
        return;
    }

    const url = process.env.API_URL + req.url.replace('/api', '');
    axios({
        method: req.method,
        url,
        data: req.body,
        headers: {
            'Authorization': `Bearer ${req.user.accessToken}`,
        },
        httpsAgent: new https.Agent({ca: caCert}),
    }).then(response => {
        res.json(response.data);
    }).catch(error => {
        res.status(error.response.status).json(error.response.data);
    });
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