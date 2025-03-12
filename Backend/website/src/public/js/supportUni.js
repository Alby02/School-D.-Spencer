document.addEventListener("DOMContentLoaded", function () {
    fetchUniversita();
});

function fetchUniversita() {
    fetch("https://localhost/api/universita")
        .then(response => {
            if (!response.ok) {
                throw new Error("Errore nel recupero dei dati");
            }
            response.json().then(displayUniversita);
        })

        .catch(error => {
            console.error("Errore durante il recupero delle università:", error);
        });
}

function displayUniversita(Universities) {
    const supportContainer = document.getElementById("supportContainer");
    supportContainer.innerHTML = "";
    
    Universities.forEach(uni => {
        const albumCol = document.createElement('div');
        albumCol.classList.add('col-12', 'col-sm-4', 'col-md-3', 'col-lg-2', 'single-album-item', 'dynamic');

        const uniElement = document.createElement("div");
        uniElement.classList.add("university-item");

        uniElement.innerHTML = `
            <div class="album-info-long">
                <h5>${uni.nome}</h5>
                <button class="macchinetteButton" data-id="${uni.id}">Apri Macchinette</button>
                <button class="aggiungiCancButton" onclick="removeUniversita('${uni.id}')">Rimuovi</button>
            </div>
        `;

        albumCol.appendChild(uniElement);
        supportContainer.appendChild(albumCol);
    });

    const addUniButton = document.createElement('div');
    addUniButton.classList.add('col-12', 'col-sm-4', 'col-md-3', 'col-lg-2', 'single-album-item', 'dynamic');
    addUniButton.innerHTML = `
        <div class="university-item">
            <div class="album-info">
                <button class="aggiungiCancButton" onclick="showAddUniversitaForm('id')">Aggiungi Università</button>
            </div>
        </div>
    `;
    supportContainer.appendChild(addUniButton);
}

function showAddUniversitaForm() {
    const addUniButton = document.querySelector(".aggiungiCancButton[onclick*='showAddUniversitaForm']");

    if (addUniButton) {
        addUniButton.style.display = "none";

        const formContainer = document.createElement('div');
        formContainer.id = "addUniversitaForm";
        formContainer.classList.add('col-12', 'col-sm-4', 'col-md-3', 'col-lg-2', 'single-album-item', 'dynamic', 'd-flex', 'justify-content-center');

        formContainer.innerHTML = `
            <div class="university-item">
                <div class="album-info-long">
                    <h5>Nome dell'università:</h5>
                    <input type="text" id="universitaNome" class="form-control mb-2" placeholder="Inserisci nome università">
                    <button class="aggiungiCancButton" onclick="addUniversita()">Aggiungi</button>
                    <button class="aggiungiCancButton" onclick="cancelUniversita()">Annulla</button>
                </div>
            </div>
        `;

        addUniButton.parentElement.appendChild(formContainer);
    }
}

function cancelUniversita() {
    const formContainer = document.getElementById("addUniversitaForm");
    const addUniButton = document.querySelector(".aggiungiCancButton[onclick*='showAddUniversitaForm']");
    
    if (formContainer) {
        formContainer.remove();
    }

    if (addUniButton) {
        addUniButton.style.display = "block"; // Riappare il pulsante
    }
}

function addUniversita() {
    const universitaNome = document.getElementById("universitaNome").value.trim();

    if (universitaNome === "") {
        alert("Il nome dell'università non può essere vuoto.");
        return;
    }

    const requestBody = {
        nome: universitaNome
    };

    fetch("https://localhost/api/universita", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify(requestBody),
    })
    .then(response => {
        if (!response.ok) {
            throw new Error("Errore durante l'aggiunta dell'università.");
        }
        return response.json();
    })
    .then(responseData => {
        fetchUniversita();
    })
    .catch(error => {
        console.error("Errore durante l'aggiunta dell'università:", error);
        alert("Errore durante l'aggiunta dell'università.");
    });
}

//Rimuove Universita
function removeUniversita(idUniversita) {
    if (!confirm("Sei sicuro di voler rimuovere questa Università?")) {
        return;
    }

    fetch(`https://localhost/api/universita/${idUniversita}`, {
        method: "DELETE",
        headers: {
            "Content-Type": "application/json"
        }
    })
    .then(response => {
        if (response.ok) {
            fetchUniversita();
        } else {
            alert("L'Università ha Macchinette e non può essere eliminata.");
        }
    })
    .catch(error => {
        console.error("Errore durante la rimozione della Universita:", error);
        alert("Errore durante la rimozione della Universita.");
    });
}

document.addEventListener("click", function (event) {
    if (event.target.classList.contains("macchinetteButton")) {
        event.preventDefault();
        const idUni = event.target.getAttribute("data-id");
        window.location.href = `supportMacchinette?id_uni=${idUni}`;
    }
});