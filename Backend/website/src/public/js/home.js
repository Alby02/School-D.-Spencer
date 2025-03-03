// Take the element we want to show
let navbar = document.querySelector('.navbar');

// Assign the onclick method to the button to display the navbar
document.querySelector('#menu-btn').onclick = () => {
  navbar.classList.toggle('active');
  searchForm.classList.remove('active');
  cartItem.classList.remove('active');
};

// Take the element we want to show
let searchForm = document.querySelector('.search-form');

// Assign the onclick method to the button to display the search form
document.querySelector('#search-btn').onclick = () => {
  searchForm.classList.toggle('active');
  navbar.classList.remove('active');
  cartItem.classList.remove('active');
};

window.onscroll = () => {
  navbar.classList.remove('active');
  searchForm.classList.remove('active');
  cartItem.classList.remove('active');
};

document.addEventListener("DOMContentLoaded", function () {
  const supportButton = document.querySelector(".support .btn");

  if (supportButton) {
      supportButton.addEventListener("click", function (event) {
          event.preventDefault();
          window.location.href = "supportUni";
      });
  }
});
