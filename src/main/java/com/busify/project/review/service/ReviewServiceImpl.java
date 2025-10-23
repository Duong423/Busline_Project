package com.busify.project.review.service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.busify.project.common.utils.JwtUtils;
import com.busify.project.review.dto.ReviewAddDTO;
import com.busify.project.review.dto.response.ReviewPageResponseDTO;
import com.busify.project.review.dto.response.ReviewResponseAddDTO;
import com.busify.project.review.dto.response.ReviewResponseDTO;
import com.busify.project.review.dto.response.ReviewResponseGetDTO;
import com.busify.project.review.dto.response.ReviewResponseListDTO;
import com.busify.project.review.entity.Review;
import com.busify.project.review.mapper.ReviewDTOMapper;
import com.busify.project.review.repository.ReviewRepository;
import com.busify.project.trip.entity.Trip;
import com.busify.project.trip.repository.TripRepository;
import com.busify.project.user.entity.User;
import com.busify.project.user.repository.UserRepository;

@Service
public class ReviewServiceImpl extends ReviewService {

        public ReviewServiceImpl(ReviewRepository reviewRepository, UserRepository userRepository,
                        TripRepository tripRepository, JwtUtils jwtUtils) {
                super(reviewRepository, userRepository, tripRepository, jwtUtils);
        }

        /**
         * Retrieves all reviews.
         *
         * @return a list of all reviews
         */
        public ReviewResponseListDTO getAllReviewsByTrip(Long tripId) {
                return new ReviewResponseListDTO(
                                reviewRepository.findByTripId(tripId).stream()
                                                .map(
                                                                review -> ReviewDTOMapper.toResponseGetDTO(review))
                                                .collect(Collectors.toList()));
        }

        /**
         * Retrieves all reviews by customer ID.
         *
         * @param customerId the ID of the customer
         * @return a list of reviews for the specified customer
         */
        public ReviewResponseListDTO getAllReviewsByCustomer(Long customerId) {
                return new ReviewResponseListDTO(
                                reviewRepository.findByCustomerId(customerId).stream()
                                                .map(
                                                                review -> ReviewDTOMapper.toResponseGetDTO(review))
                                                .collect(Collectors.toList()));
        }

        /**
         * Adds a new review based on the provided ReviewAddDTO.
         *
         * @param reviewAddDTO the DTO containing review details
         */
        public ReviewResponseDTO addReview(ReviewAddDTO reviewAddDTO) {
                final String email = jwtUtils.getCurrentUserLogin().isPresent() ? jwtUtils.getCurrentUserLogin().get()
                                : null;
                if (email == null || email.equals("")) {
                        throw new IllegalArgumentException("User not logged in");
                }
                final User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));
                
                final Trip trip = tripRepository.findById(reviewAddDTO.getTripId())
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Trip not found with ID: " + reviewAddDTO.getTripId()));
                
                // Detailed validation
                final String canReviewMessage = canReviewWithReason(reviewAddDTO.getTripId(), email);
                if (canReviewMessage != null) {
                        throw new IllegalArgumentException(canReviewMessage);
                }
                
                return toResponseAddDTO(reviewRepository.save(ReviewDTOMapper.toEntity(reviewAddDTO, user, trip)));
        }

        public ReviewResponseGetDTO getReview(Long id) {
                return reviewRepository.findById(id)
                                .map(review -> ReviewDTOMapper.toResponseGetDTO(review))
                                .orElseThrow(() -> new IllegalArgumentException("Review not found with ID: " + id));
        }

        /**
         * Deletes a review by its ID.
         *
         * @param id the ID of the review to delete
         */
        public ReviewResponseAddDTO deleteReview(Long id) {
                reviewRepository.deleteById(id);
                return new ReviewResponseAddDTO("Review deleted successfully");
        }

        /**
         * Updates an existing review.
         *
         * @param id           the ID of the review to update
         * @param reviewAddDTO the DTO containing updated review details
         * @return the updated ReviewResponseDTO
         */
        public ReviewResponseDTO updateReview(Long id, ReviewAddDTO reviewAddDTO) {
                final Review review = reviewRepository.getReferenceById(id);
                review.setComment(reviewAddDTO.getComment());
                review.setRating(reviewAddDTO.getRating());
                reviewRepository.save(review);
                return new ReviewResponseAddDTO("Review updated successfully");
        }

        public ReviewResponseListDTO getReviewsByBusOperatorId(Long busOperatorId) {
                return new ReviewResponseListDTO(
                                reviewRepository.findByBusOperatorReviews(busOperatorId, 5).stream()
                                                .map(review -> ReviewDTOMapper.toResponseGetDTO(review))
                                                .collect(Collectors.toList()));
        }

        /**
         * Converts a Review entity to a ReviewResponseAddDTO.
         *
         * @param review the Review entity to convert
         * @return a ReviewResponseAddDTO containing the success message
         */
        public ReviewResponseDTO toResponseAddDTO(Review review) {
                return new ReviewResponseAddDTO("Review created successfully");
        }

        public ReviewPageResponseDTO getAllReviews(Pageable pageable) {
                try {
                        Page<Review> reviewPage = reviewRepository.findAll(pageable);
                        Page<ReviewResponseGetDTO> dtoPage = reviewPage.map(ReviewDTOMapper::toResponseGetDTO);
                        return ReviewPageResponseDTO.fromPage(dtoPage);
                } catch (Exception e) {
                        // You can customize the error handling as needed
                        return new ReviewPageResponseDTO(Collections.emptyList(), 0, 0, 0, 0, false, false);
                }
        }

        public ReviewPageResponseDTO findByRating(Integer rating, Pageable pageable) {
                Page<Review> reviewPage = reviewRepository.findByRating(rating, pageable);
                Page<ReviewResponseGetDTO> dtoPage = reviewPage.map(ReviewDTOMapper::toResponseGetDTO);
                return ReviewPageResponseDTO.fromPage(dtoPage);
        }

        public ReviewPageResponseDTO findByRatingBetween(Integer minRating, Integer maxRating, Pageable pageable) {
                Page<Review> reviewPage = reviewRepository.findByRatingBetween(minRating, maxRating, pageable);
                Page<ReviewResponseGetDTO> dtoPage = reviewPage.map(ReviewDTOMapper::toResponseGetDTO);
                return ReviewPageResponseDTO.fromPage(dtoPage);
        }

        public ReviewPageResponseDTO findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate,
                        Pageable pageable) {
                Page<Review> reviewPage = reviewRepository.findByCreatedAtBetween(startDate, endDate, pageable);
                Page<ReviewResponseGetDTO> dtoPage = reviewPage.map(ReviewDTOMapper::toResponseGetDTO);
                return ReviewPageResponseDTO.fromPage(dtoPage);
        }

        public ReviewPageResponseDTO findByRatingAndCreatedAtBetween(Integer rating, LocalDateTime startDate,
                        LocalDateTime endDate, Pageable pageable) {
                Page<Review> reviewPage = reviewRepository.findByRatingAndCreatedAtBetween(rating, startDate, endDate,
                                pageable);
                Page<ReviewResponseGetDTO> dtoPage = reviewPage.map(ReviewDTOMapper::toResponseGetDTO);
                return ReviewPageResponseDTO.fromPage(dtoPage);
        }

        public ReviewPageResponseDTO findByCustomerFullName(String fullName, Pageable pageable) {
                Page<Review> reviewPage = reviewRepository.findByCustomerFullName(fullName, pageable);
                Page<ReviewResponseGetDTO> dtoPage = reviewPage.map(ReviewDTOMapper::toResponseGetDTO);
                return ReviewPageResponseDTO.fromPage(dtoPage);
        }

        public ReviewPageResponseDTO findByCommentContainingIgnoreCase(String keyword, Pageable pageable) {
                Page<Review> reviewPage = reviewRepository.findByCommentContainingIgnoreCase(keyword, pageable);
                Page<ReviewResponseGetDTO> dtoPage = reviewPage.map(ReviewDTOMapper::toResponseGetDTO);
                return ReviewPageResponseDTO.fromPage(dtoPage);
        }

        public ReviewPageResponseDTO findByCustomerFullNameAndCommentContaining(String fullName, String keyword,
                        Pageable pageable) {
                Page<Review> reviewPage = reviewRepository.findByCustomerFullNameAndCommentContaining(fullName, keyword,
                                pageable);
                Page<ReviewResponseGetDTO> dtoPage = reviewPage.map(ReviewDTOMapper::toResponseGetDTO);
                return ReviewPageResponseDTO.fromPage(dtoPage);
        }

        public boolean canReview(Long tripId) {
                final String email = jwtUtils.getCurrentUserLogin().isPresent() ? jwtUtils.getCurrentUserLogin().get()
                                : null;
                if (email == null || email.equals("")) {
                        throw new IllegalArgumentException("User not logged in");
                }

                return tripRepository.isUserCanReviewTrip(tripId, email);
        }
        
        /**
         * Checks if a user can review a trip and provides detailed reason if not.
         *
         * @param tripId the ID of the trip
         * @param email the email of the user
         * @return null if user can review, error message otherwise
         */
        private String canReviewWithReason(Long tripId, String email) {
                // Check if trip exists
                final Trip trip = tripRepository.findById(tripId).orElse(null);
                if (trip == null) {
                        return "Trip not found";
                }
                
                // Check if trip status is 'arrived'
                if (!trip.getStatus().toString().equalsIgnoreCase("arrived")) {
                        return "You can only review completed trips. This trip status is: " + trip.getStatus();
                }
                
                // Check if user has a booking for this trip
                final User user = userRepository.findByEmail(email).orElse(null);
                if (user == null) {
                        return "User not found";
                }
                
                // Check if user has a completed booking for this trip
                final boolean hasCompletedBooking = trip.getBookings().stream()
                        .anyMatch(booking -> booking.getCustomer().getEmail().equals(email) 
                                && booking.getStatus().toString().equalsIgnoreCase("completed"));
                
                if (!hasCompletedBooking) {
                        final boolean hasBooking = trip.getBookings().stream()
                                .anyMatch(booking -> booking.getCustomer().getEmail().equals(email));
                        
                        if (!hasBooking) {
                                return "You must have a booking for this trip to leave a review";
                        } else {
                                return "Your booking for this trip must be completed before you can leave a review";
                        }
                }
                
                // Check if user already reviewed this trip
                final boolean hasReviewed = reviewRepository.findByTripId(tripId).stream()
                        .anyMatch(review -> review.getCustomer().getEmail().equals(email));
                
                if (hasReviewed) {
                        return "You have already reviewed this trip";
                }
                
                return null; // User can review
        }
}