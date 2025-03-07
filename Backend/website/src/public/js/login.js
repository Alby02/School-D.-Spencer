function login() {
    'use strict';
    const email = document.getElementById('email').value;
    const password = document.getElementById('password').value;

    fetch('/login', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({ email, password }),
    })
    .then(response => {
        if (response.ok) {
            // Il login è riuscito, reindirizza alla pagina home
            window.location.href = '/home';
        } else {
            // Il login non è riuscito, gestisci il messaggio di errore
            return response.json().then(data => {
                const registrationMessage = document.getElementById('loginMessage');
                registrationMessage.innerHTML = data.error;
                registrationMessage.style.display = 'block';
            });
        }
    })
    .catch(error => {
        console.error('Errore durante il login:', error);
    });
}

document.getElementById('lock').onclick = function () {
    const password = document.getElementById('password');
    const lockIcon = document.getElementById('lock');

    if (password.type === 'password') {
        password.type = 'text';
        lockIcon.setAttribute('name', 'lock-open-outline');
    } else {
        password.type = 'password';
        lockIcon.setAttribute('name', 'lock-closed-outline');
    }
};

document.getElementById('loginMessage').addEventListener('click', function (event) {
    event.preventDefault();
    login();
});