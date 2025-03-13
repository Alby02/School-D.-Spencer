document.addEventListener("DOMContentLoaded", function () {
    fetchMacchinette();
});

function fetchMacchinette() {
    const urlParams = new URLSearchParams(window.location.search);
    const idUni = urlParams.get("id_uni");
    
    fetch(`https://localhost/api/macchinette/${idUni}`)
        .then(response => {
            if (!response.ok) {
                throw new Error("Errore nel recupero dei dati");
            }
            response.json().then(data => {
                displayMacchinette(data, idUni);
            });
        })

        .catch(error => {
            console.error("Errore durante il recupero delle macchinette:", error);
        });
}

function displayMacchinette(macchinette, idUni) {
    const supportContainer = document.getElementById("supportContainer");
    supportContainer.innerHTML = "";
    
    macchinette.forEach(macchinetta => {
        const albumCol = document.createElement('div');
        albumCol.classList.add('col-12', 'col-sm-4', 'col-md-3', 'col-lg-2', 'single-album-item', 'dynamic');

        const uniElement = document.createElement("div");
        uniElement.classList.add("university-item");

        console.log("Dati ricevuti:", macchinetta);
        uniElement.innerHTML = `
            <div class="album-info-long">
                <h5>${macchinetta.nome}</h5>
                <h5>Richiede Manutenzione: ${macchinetta.no_resto === "true" ? 'Sì' : 'No'}</h5>
                ${macchinetta.no_resto === "true" ? `<button class="aggiungiCancButton" onclick="resolveMaintenance('${macchinetta.id}')">Assistenza</button>` : ''}
                <h5>Richiede Manutenzione: ${macchinetta.cassa_piena === "true" ? 'Sì' : 'No'}</h5>
                ${macchinetta.cassa_piena === "true" ? `<button class="aggiungiCancButton" onclick="resolveMaintenance('${macchinetta.id}')">Assistenza</button>` : ''}
                <h5>Richiede Manutenzione: ${macchinetta.no_cialde === "true" ? 'Sì' : 'No'}</h5>
                ${macchinetta.no_cialde === "true" ? `<button class="aggiungiCancButton" onclick="resolveMaintenance('${macchinetta.id}')">Assistenza</button>` : ''}
                <h5>Richiede Manutenzione: ${macchinetta.rotta === "true" ? 'Sì' : 'No'}</h5>
                ${macchinetta.rotta === "true" ? `<button class="aggiungiCancButton" onclick="resolveMaintenance('${macchinetta.id}')">Assistenza</button>` : ''}
                <button class="aggiungiCancButton" onclick="removeMacchinetta('${macchinetta.id}')">Rimuovi</button>
            </div>
        `;
        albumCol.appendChild(uniElement);
        supportContainer.appendChild(albumCol);
    });

    const addMacchinettaButton = document.createElement('div');
    addMacchinettaButton.classList.add('col-12', 'col-sm-4', 'col-md-3', 'col-lg-2', 'single-album-item', 'dynamic');
    addMacchinettaButton.innerHTML = `
        <div class="university-item">
            <div class="album-info">
                <button class="aggiungiCancButton" onclick="showAddMacchinettaForm('${idUni}')">Aggiungi Macchinetta</button>
            </div>
        </div>
    `;
    supportContainer.appendChild(addMacchinettaButton);
}

function showAddMacchinettaForm(idUni) {
    const addMacchinettaButton = document.querySelector(".aggiungiCancButton[onclick*='showAddMacchinettaForm']");

    if (addMacchinettaButton) {
        addMacchinettaButton.style.display = "none";

        const formContainer = document.createElement('div');
        formContainer.id = "addMacchinettaForm";
        formContainer.classList.add('col-12', 'col-sm-4', 'col-md-3', 'col-lg-2', 'single-album-item', 'dynamic', 'd-flex', 'justify-content-center');

        formContainer.innerHTML = `
            <div class="university-item">
                <div class="album-info-long">
                    <h5>Nome Macchinetta:</h5>
                    <input type="text" id="idMacchinetta" class="form-control mb-2" placeholder="Inserisci ID macchinetta">
                    <input type="text" id="macchinettaNome" class="form-control mb-2" placeholder="Inserisci nome macchinetta">
                    <button class="aggiungiCancButton" onclick="addMacchinetta('${idUni}')">Aggiungi</button>
                    <button class="aggiungiCancButton" onclick="cancelAddMacchinetta()">Annulla</button>
                </div>
            </div>
        `;

        addMacchinettaButton.parentElement.appendChild(formContainer);
    }
}

function cancelAddMacchinetta() {
    const formContainer = document.getElementById("addMacchinettaForm");
    const addMacchinettaButton = document.querySelector(".aggiungiCancButton[onclick*='showAddMacchinettaForm']");
    
    if (formContainer) {
        formContainer.remove();
    }

    if (addMacchinettaButton) {
        addMacchinettaButton.style.display = "block";
    }
}

//Aggiungi macchinetta
function addMacchinetta(idUni) {
    const macchinettaNome = document.getElementById("macchinettaNome").value;
    const idMacchinetta = document.getElementById("idMacchinetta").value;

    if (macchinettaNome === "" && idMacchinetta === "") {
        alert("Il nome e ID della macchinetta sono obbligatori!");
        return;
    }

    const requestBody = {
        id: idMacchinetta,
        id_uni: idUni,
        nome: macchinettaNome
    };

    fetch("https://localhost/api/macchinette", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(requestBody)
    })
    .then(response => {
        if (response.ok) {
            fetchMacchinette();
        } else {
            alert("Errore nell'aggiungere la macchinetta.");
        }
    })
    .catch(error => {
        console.error("Errore durante l'aggiunta della macchinetta:", error);
        alert("Errore durante l'aggiunta della macchinetta.");
    });
}

//Rimuove macchinetta
function removeMacchinetta(idMacchinetta) {
    if (!confirm("Sei sicuro di voler rimuovere questa macchinetta?")) {
        return;
    }

    fetch(`https://localhost/api/macchinette/${idMacchinetta}`, {
        method: "DELETE",
        headers: {
            "Content-Type": "application/json"
        }
    })
    .then(response => {
        if (response.ok) {
            alert("Macchinetta rimossa con successo!");
            fetchMacchinette();
        } else {
            alert("Errore nella rimozione della macchinetta.");
        }
    })
    .catch(error => {
        console.error("Errore durante la rimozione della macchinetta:", error);
        alert("Errore durante la rimozione della macchinetta.");
    });
}