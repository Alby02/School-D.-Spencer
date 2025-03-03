const express = require('express');
const sequelize = require('./config/database');

const app = express();

app.set('view engine', 'ejs');
app.set('views', __dirname + '/views');

// Path file statici
app.use(express.static(__dirname + '/public'));

// Middleware
app.use(express.json());
app.use(express.urlencoded({ extended: false }));

// Routes
// home route
app.get('/', (req, res) => {
    res.render('home');
});

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
app.listen(PORT, () => {
    console.log(`Server avviato su http://localhost:${PORT}`);
  });