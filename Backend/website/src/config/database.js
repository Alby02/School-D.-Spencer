'use strict';
const { Sequelize } = require('sequelize');

const sequelize = new Sequelize('api', 'apiSpark', '123Pissir!', {
    host: 'localhost',
    dialect: 'postgres',
});

module.exports = sequelize;